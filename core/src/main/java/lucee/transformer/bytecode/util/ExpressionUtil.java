/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.transformer.bytecode.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import lucee.commons.lang.CFTypes;
import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.StringUtil;
import lucee.runtime.functions.other.CreateUniqueId;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Struct;
import lucee.transformer.Position;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.expression.var.Argument;
import lucee.transformer.bytecode.expression.var.SpreadArgument;
import lucee.transformer.bytecode.Statement;
import lucee.transformer.bytecode.visitor.OnFinally;
import lucee.transformer.bytecode.visitor.TryFinallyVisitor;
import lucee.transformer.expression.ExprString;
import lucee.transformer.expression.Expression;
import lucee.transformer.expression.literal.LitString;

public final class ExpressionUtil {

	private static final ConcurrentHashMap<String, Object> tokens = new ConcurrentHashMap<String, Object>();

	public static final Method START = new Method("exeLogStart", Types.VOID, new Type[] { Types.INT_VALUE, Types.STRING });
	public static final Method END = new Method("exeLogEnd", Types.VOID, new Type[] { Types.INT_VALUE, Types.STRING });

	public static final Method CURRENT_LINE = new Method("currentLine", Types.VOID, new Type[] { Types.INT_VALUE });

	private static Map<String, String> last = new HashMap<String, String>();

	public static void writeOutExpressionArray(BytecodeContext bc, Type arrayType, Expression[] array) throws TransformerException {
		GeneratorAdapter adapter = bc.getAdapter();
		adapter.push(array.length);
		adapter.newArray(arrayType);
		for (int i = 0; i < array.length; i++) {
			adapter.dup();
			adapter.push(i);
			array[i].writeOut(bc, Expression.MODE_REF);
			adapter.visitInsn(Opcodes.AASTORE);
		}
	}

	final static private Type ARRAY_LIST = Type.getType(java.util.ArrayList.class);
	final static private Type STRUCT = Type.getType(lucee.runtime.type.Struct.class);
	final static private Method DEFAULT_CONSTRUCTOR = Method.getMethod("void <init>()");

	private static final class RuntimeObjectSpread {
		final static public Class<?> klass = lucee.runtime.helpers.ObjectSpread.class;
		final static public Type type = Type.getType(klass);
		final static public Method spreadInto = Method.getMethod("void spreadInto(java.util.ArrayList, lucee.runtime.type.Struct)");
		final static public Type spreadIntoThrows = Type.getType(java.lang.Exception.class);
	}

	public static void writeOutSpreadArgsArray(BytecodeContext bc, Argument[] args) throws TransformerException {
		final Type ArrayList = Type.getType(java.util.ArrayList.class);

		ClassWriter cw = bc.getClassWriter();
		Method m = Method.getMethod("Object[] __fixme__foobar(lucee.runtime.PageContext)");
		GeneratorAdapter localAdapter = new GeneratorAdapter(
			Opcodes.ACC_PRIVATE,
			m,
			null,
			new Type[] { RuntimeObjectSpread.spreadIntoThrows },
			cw);

		// hold onto the existing adapter, and set the local working adapter as the BytecodeContext's current adapter
		// this ensures that descending into child expressions writes into the new method we are generating
		final GeneratorAdapter savedAdapter = bc.getAdapter();
		bc.setAdapter(localAdapter);
		
		final int result = localAdapter.newLocal(ARRAY_LIST);
		final int workingArg = localAdapter.newLocal(Type.getType(Object.class));

		localAdapter.newInstance(ARRAY_LIST);
		localAdapter.dup();
		localAdapter.invokeConstructor(ARRAY_LIST, DEFAULT_CONSTRUCTOR);
		localAdapter.storeLocal(result);
		
		for (Argument arg : args) {			
			if (arg instanceof SpreadArgument) {
				final Label expectedStruct = new Label();
				final Label done = new Label();
				
				arg._writeOut(bc, Expression.MODE_REF);
				localAdapter.storeLocal(workingArg);
				localAdapter.loadLocal(workingArg);
				localAdapter.instanceOf(STRUCT);
				localAdapter.ifZCmp(Opcodes.IFEQ, expectedStruct);

				localAdapter.loadLocal(result);
				localAdapter.loadLocal(workingArg);
				localAdapter.invokeStatic(RuntimeObjectSpread.type, RuntimeObjectSpread.spreadInto);
				localAdapter.goTo(done);
				
				localAdapter.visitLabel(expectedStruct);
				localAdapter.throwException(Type.getType(java.lang.Exception.class), "Expected a struct as operand to spread operator.");

				localAdapter.visitLabel(done);
			}
			else {
				localAdapter.loadLocal(result);
				arg.writeOut(bc, Expression.MODE_REF);
				localAdapter.invokeVirtual(ARRAY_LIST, Method.getMethod("boolean add(Object)"));
			}
		}

		localAdapter.loadLocal(result);
		localAdapter.invokeVirtual(ARRAY_LIST, Method.getMethod("Object[] toArray()"));
		localAdapter.returnValue();

		localAdapter.endMethod();

		bc.setAdapter(savedAdapter);

		GeneratorAdapter adapter = bc.getAdapter();
		adapter.loadThis();
		adapter.loadArg(0);
		adapter.invokeVirtual(bc.getTypeofThis(), m);
	}

	/**
	 * visit line number
	 * 
	 * @param adapter
	 * @param line
	 * @param silent id silent this is ignored for log
	 */
	public static void visitLine(BytecodeContext bc, Position pos) {
		if (pos != null) {
			visitLine(bc, pos.line);
		}
	}

	private static void visitLine(BytecodeContext bc, int line) {
		if (line > 0) {
			synchronized (getToken(bc.getClassName())) {
				if (!("" + line).equals(last.get(bc.getClassName() + ":" + bc.getId()))) {
					bc.visitLineNumber(line);
					last.put(bc.getClassName() + ":" + bc.getId(), "" + line);
					last.put(bc.getClassName(), "" + line);
				}
			}
		}
	}

	public static void lastLine(BytecodeContext bc) {
		synchronized (getToken(bc.getClassName())) {
			int line = Caster.toIntValue(last.get(bc.getClassName()), -1);
			visitLine(bc, line);
		}
	}

	private static Object getToken(String className) {
		Object newLock = new Object();
		Object lock = tokens.putIfAbsent(className, newLock);
		if (lock == null) {
			lock = newLock;
		}
		return lock;
	}

	/**
	 * write out expression without LNT
	 * 
	 * @param value
	 * @param bc
	 * @param mode
	 * @throws TransformerException
	 */
	public static void writeOutSilent(Expression value, BytecodeContext bc, int mode) throws TransformerException {
		Position start = value.getStart();
		Position end = value.getEnd();
		value.setStart(null);
		value.setEnd(null);
		value.writeOut(bc, mode);
		value.setStart(start);
		value.setEnd(end);
	}

	public static void writeOut(Expression value, BytecodeContext bc, int mode) throws TransformerException {
		value.writeOut(bc, mode);
	}

	public static void writeOut(final Statement s, BytecodeContext bc) throws TransformerException {
		if (ExpressionUtil.doLog(bc)) {
			final String id = CreateUniqueId.invoke();
			TryFinallyVisitor tfv = new TryFinallyVisitor(new OnFinally() {
				@Override
				public void _writeOut(BytecodeContext bc) {
					ExpressionUtil.callEndLog(bc, s, id);
				}
			}, null);

			tfv.visitTryBegin(bc);
			ExpressionUtil.callStartLog(bc, s, id);
			s.writeOut(bc);
			tfv.visitTryEnd(bc);
		}
		else s.writeOut(bc);
	}

	public static short toShortType(ExprString expr, boolean alsoAlias, short defaultValue) {
		if (expr instanceof LitString) {
			return CFTypes.toShort(((LitString) expr).getString(), alsoAlias, defaultValue);
		}
		return defaultValue;
	}

	public static void callStartLog(BytecodeContext bc, Statement s, String id) {
		call_Log(bc, START, s.getStart(), id);
	}

	public static void callEndLog(BytecodeContext bc, Statement s, String id) {
		call_Log(bc, END, s.getEnd(), id);
	}

	private static void call_Log(BytecodeContext bc, Method method, Position pos, String id) {
		if (!bc.writeLog() || pos == null || (StringUtil.indexOfIgnoreCase(bc.getMethod().getName(), "call") == -1)) return;
		try {
			GeneratorAdapter adapter = bc.getAdapter();
			adapter.loadArg(0);
			// adapter.checkCast(Types.PAGE_CONTEXT_IMPL);
			adapter.push(pos.pos);
			adapter.push(id);
			adapter.invokeVirtual(Types.PAGE_CONTEXT, method);
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
		}
	}

	public static boolean doLog(BytecodeContext bc) {
		return bc.writeLog() && StringUtil.indexOfIgnoreCase(bc.getMethod().getName(), "call") != -1;
	}
}