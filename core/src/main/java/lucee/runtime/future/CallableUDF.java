package lucee.runtime.future;

import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletRequest;

import lucee.commons.io.DevNullOutputStream;
import lucee.commons.lang.Pair;
import lucee.runtime.PageContext;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.net.http.HttpUtil;
import lucee.runtime.net.http.ReqRspUtil;
import lucee.runtime.thread.SerializableCookie;
import lucee.runtime.thread.ThreadUtil;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public class CallableUDF implements Callable<Object> {

	private UDF udf;
	private String serverName;
	private String queryString;
	private SerializableCookie[] cookies;
	private Pair<String, String>[] parameters;
	private String requestURI;
	private Pair<String, String>[] headers;
	private Struct attributes;
	private long requestTimeout;
	private ConfigWeb cw;
	private Object arg;
	private final PageContext capturedPageContext;

	// constructor for "do not capture the parent PageContext",
	// probably all callers really do want to capture, so this might be removable
	public CallableUDF(PageContext parent, UDF udf, Object arg) {
		this(parent, udf, arg, false);
	}

	public CallableUDF(PageContext parent, UDF udf, Object arg, boolean captureParentPageContext) {
		// this.template=page.getPageSource().getRealpathWithVirtual();
		HttpServletRequest req = parent.getHttpServletRequest();
		serverName = req.getServerName();
		queryString = ReqRspUtil.getQueryString(req);
		cookies = SerializableCookie.toSerializableCookie(ReqRspUtil.getCookies(req, parent.getWebCharset()));
		parameters = HttpUtil.cloneParameters(req);
		requestURI = req.getRequestURI();
		headers = HttpUtil.cloneHeaders(req);
		attributes = HttpUtil.getAttributesAsStruct(req);
		requestTimeout = parent.getRequestTimeout();

		cw = parent.getConfig();
		this.udf = udf;
		this.arg = arg;

		if (captureParentPageContext) {
			capturedPageContext = parent;
		}
		else {
			capturedPageContext = null;
		}
	}

	@Override
	public Object call() throws Exception {
		PageContext pc = null;
		ThreadLocalPageContext.register(pc);

		DevNullOutputStream os = DevNullOutputStream.DEV_NULL_OUTPUT_STREAM;

		final boolean usingCapturedPageContext = capturedPageContext != null;
		if (usingCapturedPageContext) {
			pc = capturedPageContext;
		}
		else {
			pc = ThreadUtil.createPageContext(cw, os, serverName, requestURI, queryString, SerializableCookie.toCookies(cookies), headers, null, parameters, attributes, true, -1);
			pc.setRequestTimeout(requestTimeout);
		}

		try {
			return udf.call(pc, arg == Future.ARG_NULL ? new Object[] {} : new Object[] { arg }, true);
		}
		finally {
			//
			// not sure about who is in charge of releasing a captured page context
			// if we've captured the caller's page context,
			//   - we could run longer than it, and we're responsible for releasing it
			//   - we could finish before it, and it's responsible for releasing it
			// is there a bump counter or semaphore where we always release, and it decrements, and if it's zero, it truly gets released ?
			//
			
			// if we made a fresh page context, we can release it
			if (!usingCapturedPageContext) {
				pc.getConfig().getFactory().releasePageContext(pc);
			}
		}

	}

}
