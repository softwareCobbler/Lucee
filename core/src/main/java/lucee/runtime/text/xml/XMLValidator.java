/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
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
 **/
package lucee.runtime.text.xml;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import lucee.commons.lang.StringUtil;
import lucee.runtime.exp.XMLException;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Array;
import lucee.runtime.type.ArrayImpl;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.util.KeyConstants;

public class XMLValidator extends XMLEntityResolverDefaultHandler {

	@Override
	public InputSource resolveEntity(String publicID, String systemID) throws SAXException {
		// print.out(publicID+":"+systemID);
		return super.resolveEntity(publicID, systemID);
	}

	private Array warnings;
	private Array errors;
	private Array fatals;
	private boolean hasErrors;
	private String strSchema;

	public XMLValidator(InputSource validator, String strSchema) {
		super(validator);
		this.strSchema = strSchema;
	}

	private void release() {
		warnings = null;
		errors = null;
		fatals = null;
		hasErrors = false;
	}

	@Override
	public void warning(SAXParseException spe) {
		log(spe, "Warning", warnings);
	}

	@Override
	public void error(SAXParseException spe) {
		hasErrors = true;
		log(spe, "Error", errors);
	}

	@Override
	public void fatalError(SAXParseException spe) throws SAXException {
		hasErrors = true;
		log(spe, "Fatal Error", fatals);
	}

	private void log(SAXParseException spe, String type, Array array) {
		StringBuffer sb = new StringBuffer("[" + type + "] ");

		String id = spe.getSystemId();
		if (!StringUtil.isEmpty(id)) {
			int li = id.lastIndexOf('/');
			if (li != -1) sb.append(id.substring(li + 1));
			else sb.append(id);
		}
		sb.append(':');
		sb.append(spe.getLineNumber());
		sb.append(':');
		sb.append(spe.getColumnNumber());
		sb.append(": ");
		sb.append(spe.getMessage());
		sb.append(" ");
		array.appendEL(sb.toString());
	}

	public Struct validate(InputSource xml, Struct result) throws XMLException {
		if (result == null) {
			warnings = new ArrayImpl();
			errors = new ArrayImpl();
			fatals = new ArrayImpl();

			result = new StructImpl();
			result.setEL(KeyConstants._warnings, warnings);
			result.setEL(KeyConstants._errors, errors);
			result.setEL(KeyConstants._fatalerrors, fatals);
		}
		else {
			warnings = getArray(result, KeyConstants._warnings);
			errors = getArray(result, KeyConstants._errors);
			fatals = getArray(result, KeyConstants._fatalerrors);
			hasErrors = !getBoolean(result, KeyConstants._status);
		}

		try {
			XMLReader parser = XMLUtil.createXMLReader();
			parser.setContentHandler(this);
			parser.setErrorHandler(this);
			parser.setEntityResolver(this);
			parser.setFeature("http://xml.org/sax/features/validation", true);
			parser.setFeature("http://apache.org/xml/features/validation/schema", true);
			parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
			// if(!validateNamespace)
			if (!StringUtil.isEmpty(strSchema)) parser.setProperty("http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation", strSchema);
			parser.parse(xml);
		}
		catch (Exception e) {

			throw new XMLException(e);
		}

		result.setEL(KeyConstants._status, Caster.toBoolean(!hasErrors));

		release();
		return result;
	}

	private Array getArray(Struct result, Key key) {
		Array arr = Caster.toArray(result.get(key, null), null);
		if (arr != null) return arr;
		return new ArrayImpl();
	}

	private boolean getBoolean(Struct result, Key key) {
		return Caster.toBooleanValue(result.get(key, null), true);
	}

}