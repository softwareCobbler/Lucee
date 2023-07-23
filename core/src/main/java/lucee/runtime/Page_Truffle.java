package lucee.runtime;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.lang.ref.SoftReference;

import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.component.ImportDefintion;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;
import lucee.runtime.type.UDFProperties;
import lucee.runtime.util.IO;

public class Page_Truffle extends Page {

    private org.graalvm.polyglot.Value callTarget;

    Page_Truffle(org.graalvm.polyglot.Value callTarget) {
        this.callTarget = callTarget;
    }

	public long getVersion() {
		return -1;
	}

	public Object call(final PageContext pc) throws Throwable {
        callTarget.execute();
		return null;
	}

	public long getSourceLastModified() {
		return 0;
	}

	public long getCompileTime() {
		return 0;
	}

	public Object udfCall(final PageContext pageContext, final UDF udf, final int functionIndex) throws Throwable {
        throw new RuntimeException("Page_Truffle invoked udfCall?");
	}

	public void threadCall(final PageContext pageContext, final int threadIndex) throws Throwable {
        throw new RuntimeException("Page_Truffle invoked threadCall?");
    }

	public Object udfDefaultValue(final PageContext pc, final int functionIndex, final int argumentIndex, final Object defaultValue) {
		throw new RuntimeException("Page_Truffle invoked udfDefaultValue?");
	}

	public ImportDefintion[] getImportDefintions() {
		throw new RuntimeException("Page_Truffle invoked getImportDefinitions");
	}

	public CIPage[] getSubPages() {
		throw new RuntimeException("Page_Truffle invoked getSubPages");
	}
}