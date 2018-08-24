package io.xview;

import javax.script.Bindings;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class XViewScriptObject {

    private XView xview;

    private Bindings bindings;

    private OutputStream outputStream;

    public XViewScriptObject(XView xview, Bindings bindings, OutputStream outputStream) {
        this.xview = xview;
        this.bindings = bindings;
        this.outputStream = outputStream;
    }

    public void print(Object data) throws IOException {
        String text = data == null ? null : data.toString();

        if (text == null) {
            return;
        }

        this.outputStream.write(text.getBytes());
    }

    /**
     * 通过当前xview文件（实际是XSD约束文件）来获取同目录下的另一个文件的可访问路径。
     */
    public String path(String path) {
        return this.path(this.xview.getUri(), path);
    }

    /**
     * 通过一个路径来获取另一个文件的关联路径。
     * 例如：
     * path("/hello/1.png", "images/2.png") 返回 "/hello/images/2.png"
     */
    public String path(String uri, String path) {
        String loadUri;

        int offset = uri.replaceAll("\\\\", "/").lastIndexOf('/');
        if (offset != -1) {
            path = path.replaceAll("\\\\", "/");
            loadUri = uri.substring(0, offset + 1) + (path.startsWith("/") ? path.substring(1) : path);
        } else {
            loadUri = path;
        }

        return loadUri;
    }

    public void execute(String xsd, String tag, Map<String, Object> callAttributes, Object processChildNodesFunction) throws Exception {
        // 根据引用组件的XSD文档获取组件xview文件路径（同目录）
        String uri = this.path(xsd, tag + XView.FILE_EXT);

        // 通过xview文件加载目标组件
        XView targetXview = this.xview.getXViewManager().load(uri);

        // 由于组件之间是互相隔离的（不是同一个Bindings），所以需要将父组件的Bindings同步过来，这样子组件就可以访问父组件的变量
        Map<String, Object> env = new HashMap<>();
        env.putAll(this.bindings);
        env.putAll(callAttributes);

        targetXview.execute(this.outputStream, env, processChildNodesFunction);
    }

}
