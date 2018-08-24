package io.xview.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ScriptEnvironment {

	public static final String CALL_PARAMS_NAME = "__params__";
	
	private ScriptEngineManager scriptEngineManager;

	private Map<String, ScriptEngine> scriptEngineMapping;
	
	public ScriptEnvironment() {
		this.scriptEngineManager = new ScriptEngineManager();
		this.scriptEngineMapping = new HashMap<>();
	}
	
	public ScriptEngineManager getScriptEngineManager() {
		return this.scriptEngineManager;
	}

	public ScriptEngineFactory getScriptEngineFactory(String language) throws ScriptException {
		return this.getScriptEngine(language).getFactory();
	}

	public ScriptEngine getScriptEngine(String language) throws ScriptException {
		ScriptEngine scriptEngine = this.scriptEngineMapping.get(language = language.toLowerCase());
		
		if (scriptEngine != null) {
			return scriptEngine;
		}
		
		ScriptEngineFactory scriptEngineFactory = null;
		
		for (ScriptEngineFactory i : this.scriptEngineManager.getEngineFactories()) {
			for (String name : i.getNames()) {
				if (name.equalsIgnoreCase(language)) {
					scriptEngineFactory = i;
					break;
				}
			}
		}
		
		if (scriptEngineFactory == null) {
			throw new ScriptException("找不到[" + language + "]语言的脚本引擎。");
		} else if ("Oracle Nashorn".equals(scriptEngineFactory.getEngineName())) {
//			String[] args = new String[] { };
//			String[] args = new String[] { "--optimistic-types=false", "--language=es5", "--persistent-code-cache=true"/*, "--print-code=false"*/, "--print-mem-usage=false" };
//			String[] args = new String[] { "--language=es6" };
//			jdk.nashorn.api.scripting.NashornScriptEngineFactory nashornScriptEngineFactory = (jdk.nashorn.api.scripting.NashornScriptEngineFactory) scriptEngineFactory;
//			scriptEngine = nashornScriptEngineFactory.getScriptEngine(args);
			
			scriptEngine = scriptEngineFactory.getScriptEngine();
		} else if ("Mozilla Rhino".equals(scriptEngineFactory.getEngineName())) {
//			com.sun.script.javascript.RhinoScriptEngineFactory rhinoScriptEngineFactory = (com.sun.script.javascript.RhinoScriptEngineFactory) scriptEngineFactory;
			
			scriptEngine = scriptEngineFactory.getScriptEngine();
		} else {
			scriptEngine = scriptEngineFactory.getScriptEngine();
		}
		
		this.scriptEngineMapping.put(language, scriptEngine);
		return scriptEngine;
	}
	
	public ScriptFragment createScriptFragment(String language, boolean tryCompile, String filename, String script) throws ScriptException {
		ScriptEngine scriptEngine = this.getScriptEngine(language);
		
		if (tryCompile && scriptEngine instanceof Compilable) {
			/*
			 * 编译。
			 */
			synchronized (scriptEngine) {
				Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
				
				Object oldFilename = bindings.put(ScriptEngine.FILENAME, filename);
				try {
					return new ScriptFragment(script, filename, ((Compilable) scriptEngine).compile(script));
				} finally {
					bindings.put(ScriptEngine.FILENAME, oldFilename);
				}
			}
		} else {
			/*
			 * 解释。
			 */
			return new ScriptFragment(script, filename, scriptEngine);
		}
	}
	
	public ScriptFragment createCallScriptFragment(String language, boolean tryCompile, String name, int argc) throws ScriptException {
		ScriptEngine scriptEngine = this.getScriptEngine(language);

		List<String> args = new ArrayList<>();
		for (int i = 0; i < argc; i++) {
			args.add(CALL_PARAMS_NAME + ".length > " + i + " ? " + CALL_PARAMS_NAME + "[" + i + "] : null");
		}
		
		String argsString = scriptEngine.getFactory().getMethodCallSyntax(null, name, args.toArray(new String[0]));
		
//		StringBuilder sb = new StringBuilder();
//		sb.append(name).append("(");
//		for (int i = 0; i < argc; i++) {
//			sb.append(CALL_PARAMS_NAME).append(".length > ").append(i).append(" ? ").append(CALL_PARAMS_NAME).append("[").append(i).append("] : null");
//			if (i < argc - 1) {
//				sb.append(", ");
//			}
//		}
//		sb.append(")");
		
		return this.createScriptFragment(language, tryCompile, argsString, "call#" + name);
	}
	
	public static Object eval(ScriptEngine scriptEngine, Bindings bindings, String script, String filename) throws ScriptException {
		Object oldFilename = bindings.put(ScriptEngine.FILENAME, filename);
		
		try {
			return scriptEngine.eval(script, bindings);
		} finally {
			bindings.put(ScriptEngine.FILENAME, oldFilename);
		}
	}
	
	public static Object eval(ScriptEngine scriptEngine, Bindings bindings, String script) throws ScriptException {
		return eval(scriptEngine, bindings, script, null);
	}
	
}
