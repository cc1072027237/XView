package io.xview.script;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * 脚本片段。
 */
public class ScriptFragment {
	
	private static final Object[] EMPTY_PARAMS = new Object[0];

	private String script;
	
	private String filename;
	
	private CompiledScript compiledScript;
	
	private ScriptEngine scriptEngine;
	
	public ScriptFragment(String script, String filename, CompiledScript compiledScript) {
		this.script = script;
		this.filename = filename;
		this.compiledScript = compiledScript;
	}
	
	public ScriptFragment(String script, String filename, ScriptEngine scriptEngine) {
		this.script = script;
		this.filename = filename;
		this.scriptEngine = scriptEngine;
	}

	public String getScript() {
		return script;
	}

	public String getFilename() {
		return filename;
	}

	public CompiledScript getCompiledScript() {
		return compiledScript;
	}

	public ScriptEngine getScriptEngine() {
		return scriptEngine;
	}
	
	public Object eval(Bindings bindings) throws ScriptException {
		if (this.compiledScript != null) {
			return this.compiledScript.eval(bindings);
		} else {
			return ScriptEnvironment.eval(this.scriptEngine, bindings, this.script, this.filename);
		}
	}
	
	public Object call(Bindings bindings, Object... args) throws ScriptException {
		if (args != null && args.length > 0) {
			bindings.put(ScriptEnvironment.CALL_PARAMS_NAME, args);
		} else {
			bindings.put(ScriptEnvironment.CALL_PARAMS_NAME, EMPTY_PARAMS);
		}
		
		try {
			return this.eval(bindings);
		} finally {
			bindings.remove(ScriptEnvironment.CALL_PARAMS_NAME);
		}
	}
	
}
