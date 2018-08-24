package io.xview;

import io.xview.script.ScriptFragment;
import org.w3c.dom.*;

public class XViewScript {

    private XView xview;

    private String script;

    private ScriptFragment scriptFragment;

    public XViewScript(XView xview) {
        this.xview = xview;

        CodeBuilder codeBuilder = new CodeBuilder();
        this.buildCode(this.xview.getXViewXml().getRootElement(), codeBuilder);
        this.script = codeBuilder.getScript();
    }

    public String getScript() {
        return this.script;
    }

    public ScriptFragment getScriptFragment() {
        return scriptFragment;
    }

    public void setScriptFragment(ScriptFragment scriptFragment) {
        this.scriptFragment = scriptFragment;
    }

    private void buildCode(Node node, CodeBuilder codeBuilder) {
        if (node instanceof Text) {
            String s = ((Text) node).getWholeText();
            if (s != null) {
                s = s.trim();
                if (!s.isEmpty()) {
                    codeBuilder.appendOutput(toMiniString(s));
                }
            }
        } else if (node instanceof Element) {
            Name name = Name.parse(node.getNodeName());

            /*
             * 输出自己的标记头。
             */
            boolean isClosed = false;
            if (name.getPrefix() == null) {
                isClosed = writeBeginTag(codeBuilder, node);
            }

            /*
             * 输出子内容。
             */
            if (name.getPrefix() == null) {
                if (!isClosed) {
                    this.forEachChildNodes(codeBuilder, node);
                }
            } else {
                XViewXml.Namespace namespace = this.xview.getXViewXml().getNamespaceMapping().get(name.getPrefix());

                if (namespace != null) {
                    if (XView.XVIEW_NAMESPACE.equalsIgnoreCase(namespace.getTargetNamespace())) {
                        if (XView.TAG_SCRIPT.equalsIgnoreCase(name.getName())) {
                            // xview:script 直接追加脚本
                            codeBuilder.appendScript("\n").appendScript(toMiniString(node.getTextContent())).appendScript("\n");
                        } else if (XView.TAG_VIEW.equalsIgnoreCase(name.getName())) {
                            // xview:view 递归解析
                            this.forEachChildNodes(codeBuilder, node);
                        } else if (XView.TAG_PROCESS_CHILD_NODES.equalsIgnoreCase(name.getName())) {
                            // xview:processChildNodes 调用子内容
                            codeBuilder.appendScript("if(").appendScript(XView.PROCESS_CHILD_NODES_FUNCTION).appendScript("){eval(")
                                    .appendScript(XView.PROCESS_CHILD_NODES_FUNCTION).appendScript(")();}\n");
                        } else {
                            throw new XViewException(this.xview.getUri() + " -> unknow tag [" + ((Element) node).getTagName() + "]");
                        }
                    } else {
                        // xxx:xxx 引用其他xview，递归解析（代码闭包传递）

                        // 封装子内容代码
                        CodeBuilder closureCodeBuilder = new CodeBuilder();
                        closureCodeBuilder.appendScript("function ").appendScript("(){");
                        this.forEachChildNodes(closureCodeBuilder, node);
                        closureCodeBuilder.appendScript("}");

                        // 生成调用参数
                        StringBuilder callAttributeScript = new StringBuilder();
                        callAttributeScript.append("(function(){");
                        NamedNodeMap attributes = node.getAttributes();
                        if (attributes != null) {
                            callAttributeScript.append("var HashMap=Java.type(\"java.util.HashMap\");");
                            callAttributeScript.append("var map=new HashMap();");

                            for (int i = 0; i < attributes.getLength(); i++) {
                                Name attributeName = Name.parse(attributes.item(i).getNodeName());
                                if (attributeName.getPrefix() == null) {
                                    attributeName.setPrefix(name.getPrefix());
                                }

                                if (name.getPrefix().equalsIgnoreCase(attributeName.getPrefix())) {
                                    String varName = "value$" + i;
                                    String varValue = attributes.item(i).getNodeValue();
                                    callAttributeScript.append("var ").append(varName).append("=").append((varValue == null || varValue.trim().isEmpty()) ? "''" : varValue).append(";");
                                    callAttributeScript.append("if(").append(varName).append("){");
                                    callAttributeScript.append("map.put(\"").append(attributeName.getName()).append("\",").append(varName).append(");}");
                                }
                            }
                            callAttributeScript.append("return map;");
                        }
                        callAttributeScript.append("})()");

                        codeBuilder.appendScript("xview.execute(\"");
                        codeBuilder.appendScript(namespace.getUrl());
                        codeBuilder.appendScript("\",\"");
                        codeBuilder.appendScript(name.getName());
                        codeBuilder.appendScript("\",");
                        codeBuilder.appendScript(callAttributeScript.toString());
                        codeBuilder.appendScript(",\"");
                        codeBuilder.appendScript(toSafeString(closureCodeBuilder.getScript()));
                        codeBuilder.appendScript("\");\n");
                    }
                } else {
                    throw new XViewException(this.xview.getUri() + " -> unknow tag [" + ((Element) node).getTagName() + "]");
                }
            }

            /*
             * 输出自己的标记尾。
             */
            if (name.getPrefix() == null) {
                if (!isClosed) {
                    writeEndTag(codeBuilder, node);
                }
            }

            return;
        }
    }

    private void forEachChildNodes(CodeBuilder codeBuilder, Node node) {
        NodeList childNodes = node.getChildNodes();

        if (childNodes != null && childNodes.getLength() > 0) {
            for (int i = 0; i < childNodes.getLength(); i++) {
                this.buildCode(childNodes.item(i), codeBuilder);
            }
        }
    }

    private static boolean writeBeginTag(CodeBuilder codeBuilder, Node node) {
        codeBuilder.appendOutput("<").appendOutput(node.getNodeName());

        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null && attributes.getLength() > 0) {
            for (int i = 0; i < attributes.getLength(); i++) {
                codeBuilder.appendOutput(" ");
                codeBuilder.appendOutput(attributes.item(i).getNodeName());
                codeBuilder.appendOutput("=\"");
                codeBuilder.appendOutput(attributes.item(i).getNodeValue());
                codeBuilder.appendOutput("\"");
            }
        }

        NodeList childNodes = node.getChildNodes();
        if (childNodes != null && childNodes.getLength() > 0) {
            codeBuilder.appendOutput(">");
            return false;
        } else {
            codeBuilder.appendOutput("/>");
            return true;
        }
    }

    private static void writeEndTag(CodeBuilder codeBuilder, Node node) {
        codeBuilder.appendOutput("</").appendOutput(node.getNodeName()).appendOutput(">");
    }

    private static class CodeBuilder {

        private StringBuilder script = new StringBuilder();

        private StringBuilder peddingOutput;

        public String getScript() {
            this.flushOutput();

            return this.script.toString();
        }

        public CodeBuilder appendOutput(String output) {
            int begin;

            while ((begin = output.indexOf("${")) != -1) {
                int end = output.indexOf("}", begin);
                if (end == -1) {
                    break;
                }

                this.privateAppendOutput(output.substring(0, begin));

                this.appendScript("xview.print(");
                this.appendScript(output.substring(begin + "${".length(), end));
                this.appendScript(");\n");

                output = output.substring(end + "}".length());
            }

            return this.privateAppendOutput(output);
        }

        private CodeBuilder privateAppendOutput(String output) {
            if (output == null || output.isEmpty()) {
                return this;
            }

            if (this.peddingOutput == null) {
                this.peddingOutput = new StringBuilder();
            }

            this.peddingOutput.append(output);
            return this;
        }

        private void flushOutput() {
            if (this.peddingOutput != null) {
                this.script.append("xview.print(\"").append(toSafeString(this.peddingOutput.toString())).append("\");");
                this.peddingOutput = null;
            }
        }

        public CodeBuilder appendScript(String script) {
            this.flushOutput();

            this.script.append(script);
            return this;
        }
    }

    private static class Name {

        private String prefix;

        private String name;

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static Name parse(String str) {
            Name name = new Name();
            int offset = str.indexOf(':');

            if (offset != -1) {
                name.prefix = str.substring(0, offset);
                name.name = str.substring(offset + 1 );
            } else {
                name.name = str;
            }

            return name;
        }

    }

    private static String toSafeString(String s) {
        return s.replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"");
    }

    private static String toMiniString(String s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String i : s.replaceAll("\r", "").split("\n")) {
            if (i != null && !(i = i.trim()).isEmpty()) {
                stringBuilder.append(i).append("\n");
            }
        }
        return stringBuilder.deleteCharAt(stringBuilder.length() - 1).toString();
    }

}
