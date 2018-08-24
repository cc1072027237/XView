package io.xview;


import io.xview.script.ScriptEnvironment;
import io.xview.script.ScriptFragment;

import javax.script.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class XViewManager {

    public static final String SCRIPT_LANGUAGE = "ecmascript";

    private XViewLoader xviewLoader;
    private boolean validateSchema;

    private int bufferedBindingsCount;
    private boolean compileFlag;

    private ScriptEnvironment scriptEnvironment;
    private List<Bindings> bufferedBindings;

    public XViewManager(XViewLoader xviewLoader, boolean validateSchema, int bufferedBindingsCount, boolean compileFlag) {
        this.xviewLoader = xviewLoader;
        this.validateSchema = validateSchema;
        this.compileFlag = compileFlag;
        this.bufferedBindingsCount = bufferedBindingsCount;

        this.init();
    }

    private void init() {
        this.scriptEnvironment = new ScriptEnvironment();

        this.bufferedBindings = new ArrayList<>();
        for (int i = 0; i < this.bufferedBindingsCount; i++) {
            try {
                this.bufferedBindings.add(this.scriptEnvironment.getScriptEngine(SCRIPT_LANGUAGE).createBindings());
            } catch (ScriptException e) {
                throw new XViewException(e);
            }
        }
    }

    private Bindings popBindings() {
        if (!this.bufferedBindings.isEmpty()) {
            synchronized (this.bufferedBindings) {
                if (!this.bufferedBindings.isEmpty()) {
                    return this.bufferedBindings.remove(0);
                }
            }
        }

        try {
            return this.scriptEnvironment.getScriptEngine(SCRIPT_LANGUAGE).createBindings();
        } catch (ScriptException e) {
            throw new XViewException(e);
        }
    }

    private void pushBindings(Bindings bindings) {
        if (this.bufferedBindings.size() < this.bufferedBindingsCount) {
            bindings.clear();

            synchronized (this.bufferedBindings) {
                if (this.bufferedBindings.size() < this.bufferedBindingsCount) {
                    this.bufferedBindings.add(bindings);
                }
            }
        }
    }

    protected void execute(XView xview, Map<String, Object> attributes, OutputStream outputStream) {
        if (xview.getXViewManager() != this) {
            throw new XViewException(xview.getUri() + " -> XViewManager not match");
        }

        Bindings bindings = this.popBindings();

        try {
            bindings.putAll(attributes);
            bindings.put("xview", new XViewScriptObject(xview, bindings, outputStream));

            ScriptFragment scriptFragment = xview.getXViewScript().getScriptFragment();
            if (scriptFragment == null) {
                String filename = xview.getUri() + ".js";
                scriptFragment = this.scriptEnvironment.createScriptFragment(SCRIPT_LANGUAGE, this.compileFlag, filename, xview.getXViewScript().getScript());
                xview.getXViewScript().setScriptFragment(scriptFragment);
            }

            scriptFragment.eval(bindings);
        } catch (Exception e) {
            throw new XViewException(xview.getUri(), e);
        } finally {
            this.pushBindings(bindings);
        }
    }

    public XView load(String uri) {
        XView xview = null;

        try (InputStream inputStream = this.xviewLoader.load(uri)) {
            if (inputStream != null) {
                xview = new XView(this, uri, inputStream, this.validateSchema);
            }
        } catch (Exception e) {
            throw new XViewException(uri, e);
        }

        if (xview == null) {
            throw new XViewException("unable to load [" + uri + "]");
        } else {
            return xview;
        }
    }

    public static interface XViewLoader {

        InputStream load(String uri);

    }

    public static class DefaultXViewLoader implements XViewLoader {

        @Override
        public InputStream load(String uri) {
            InputStream inputStream;

            if ((inputStream = this.getClass().getResourceAsStream(uri)) != null) {
                return inputStream;
            }

            if ((inputStream = this.loadFromFile(uri)) != null) {
                return inputStream;
            }

            if ((inputStream = this.loadFromUrl(uri)) != null) {
                return inputStream;
            }

            return null;
        }

        private InputStream loadFromUrl(String uri) {
            try {
                return new URL(uri).openStream();
            } catch (MalformedURLException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
        }

        private InputStream loadFromFile(String uri) {
            File file = new File(uri);

            if (file.isFile()) {
                try {
                    return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

}
