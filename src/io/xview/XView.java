package io.xview;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * 表示一个XView文件。
 * 处理流程：
 * 1、读取XML文件并分析namespace信息
 * 2、格式化XML并编译为javascript
 * 3、运行javascript
 */
public class XView {

    public static final String XVIEW_NAMESPACE = "xview";

    public static final String FILE_EXT = ".xview";
    
    public static final String TAG_VIEW = "view";
    public static final String TAG_SCRIPT = "script";
    public static final String TAG_PROCESS_CHILD_NODES = "process_child_nodes";

    public static final String PROCESS_CHILD_NODES_FUNCTION = "__process_child_nodes";

    private XViewManager xviewManager;

    private String uri;

    private boolean validateSchema;

    private XViewXml xviewXml;

    private XViewScript xviewScript;

    public XView(XViewManager xviewManager, String uri, InputStream xviewInputStream) {
        this(xviewManager, uri, xviewInputStream, false);
    }

    public XView(XViewManager xviewManager, String uri, InputStream xviewInputStream, boolean validateSchema) {
            this.xviewManager = xviewManager;
            this.uri = uri;
            this.validateSchema = validateSchema;

        try {
            this.xviewXml = new XViewXml(xviewInputStream, validateSchema);
        } catch (IOException | SAXException | ParserConfigurationException  e) {
            throw new XViewException(this.uri, e);
        }
        this.xviewScript = new XViewScript(this);
    }

    public XViewManager getXViewManager() {
        return xviewManager;
    }

    public String getUri() {
        return uri;
    }

    public boolean isValidateSchema() {
        return validateSchema;
    }

    public XViewXml getXViewXml() {
        return xviewXml;
    }

    public XViewScript getXViewScript() {
        return xviewScript;
    }

    public void execute(OutputStream outputStream, Map<String, Object> callAttributes, Object processChildNodesFunction) {
        Map<String, Object> calledAttributes = new HashMap<>();

        // 填充默认值
        Map<String, String> defaultAttributes = this.getXViewXml().getAttributes();
        if (defaultAttributes != null && !defaultAttributes.isEmpty()) {
            calledAttributes.putAll(defaultAttributes);
        }

        // 填充传入的调用参数（只写入在目标xview中声明过的参数）
        if (callAttributes != null && !callAttributes.isEmpty()) {
            for (String key : callAttributes.keySet()) {
                if (!defaultAttributes.containsKey(key)) {
                    continue;
                }

                Object value = callAttributes.get(key);
                if (value != null) {
                    if (value instanceof String) {
                        String textValue = (String) value;
                        if (textValue.trim().isEmpty()) {
                            continue;
                        }
                    }

                    calledAttributes.put(key, value);
                }
            }
        }

        calledAttributes.put(XView.PROCESS_CHILD_NODES_FUNCTION, processChildNodesFunction);

        // 调用此文件
        this.xviewManager.execute(this, calledAttributes, outputStream);
    }

    static int count = 0;
    static long startTime = System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        XViewManager manager = new XViewManager(new XViewManager.DefaultXViewLoader(), false, 50, true);

        for (int i = 0; i < 1; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (count % 100 == 0) {
                            synchronized (manager) {
                                if (System.currentTimeMillis() - startTime > 1000) {
                                    System.err.println(count);
                                    count = 0;
                                    startTime = System.currentTimeMillis();
                                }
                            }
                        }

                        XView xview = manager.load("/io/xview/test/a.page.xview");
                        System.out.println(xview.getXViewScript().getScript());
//                        xview.execute(new EmptyOutputStream(), null, null);
//                        count++;
                        xview.execute(System.err, null, null);
                        break;
                    }
                }
            }).start();
        }
    }

    private static class EmptyOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {

        }

        public void write(byte b[], int off, int len) throws IOException {

        }

        public void write(byte b[]) throws IOException {

        }

    }

}