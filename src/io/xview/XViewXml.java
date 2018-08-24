package io.xview;

import jdk.internal.util.xml.impl.Input;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class XViewXml {

    private Map<String, Namespace> namespaceMapping = new HashMap<>();

    private Element rootElement;

    public XViewXml(InputStream inputStream) throws IOException, SAXException, ParserConfigurationException {
        this(inputStream, false);
    }

    public XViewXml(InputStream inputStream, boolean validateSchema) throws IOException, SAXException, ParserConfigurationException {
        byte[] bytes = null;

        if (validateSchema) {
            bytes = readBytes(inputStream);
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes)) {
                this.validate(byteArrayInputStream);
            }
        }

        Document document;
        if (bytes != null) {
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes)) {
                byteArrayInputStream.reset();
                document = this.read(byteArrayInputStream);
            }
        } else {
            document = this.read(inputStream);
        }

        this.parse(document);
    }

    /**
     * 验证XML文档。
     */
    private void validate(InputStream inputStream) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema();
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(inputStream));
    }

    /**
     * 读取XML文档。
     */
    private Document read(InputStream inputStream) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setValidating(false);
        documentBuilderFactory.setXIncludeAware(true);
        documentBuilderFactory.setExpandEntityReferences(true);
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setCoalescing(true);
        documentBuilderFactory.setIgnoringComments(true);
        documentBuilderFactory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(inputStream);
    }

    /**
     * 提取XML文档导入信息。
     */
    private void parse(Document document) {
        this.rootElement = document.getDocumentElement();
        NamedNodeMap attributes = this.rootElement.getAttributes();

        /*
         * 解析根元素的namespace属性集
         */
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);

            String attributeName = attribute.getNodeName();
            String attributeValue = attribute.getNodeValue();

            String prefixName = null;
            String suffixName = null;
            int prefixOffset;
            if ((prefixOffset = attributeName.indexOf(":")) != -1) {
                prefixName = attributeName.substring(0, prefixOffset);
                suffixName = attributeName.substring(prefixOffset + 1);
            }

            if (prefixName != null) {
                if ("xmlns".equalsIgnoreCase(prefixName)) {
                    if (!this.namespaceMapping.containsKey(suffixName)) {
                        this.namespaceMapping.put(suffixName, new Namespace(attributeValue));
                    } else {
                        this.namespaceMapping.get(suffixName).setTargetNamespace(attributeValue);
                    }
                } else {
                    if (!this.namespaceMapping.containsKey(prefixName)) {
                        this.namespaceMapping.put(prefixName, new Namespace(null));
                    }

                    this.namespaceMapping.get(prefixName).getAttribute().put(suffixName, attributeValue);
                }
            }
        }

        /*
         * 获取Schema文件映射表（schemaLocation属性）
         */
        Namespace xsi = null;
        for (String i : this.namespaceMapping.keySet()) {
            Namespace namespace = this.namespaceMapping.get(i);

            if ("http://www.w3.org/2001/XMLSchema-instance".equalsIgnoreCase(namespace.getTargetNamespace())) {
                xsi = namespace;
                break;
            }
        }

        /*
         * 将Schema文件映射表更新到对象映射
         */
        if (xsi != null) {
            String schemaLocation = xsi.getAttribute().get("schemaLocation");

            if (schemaLocation != null) {
                List<String> schemaLocations = split(schemaLocation, "\\s+");

                if (schemaLocations.size() % 2 != 0) {
                    throw new XViewException("[schemaLocation] must be even number");
                }

                for (int i = 0; i < schemaLocations.size(); i += 2) {
                    String key = schemaLocations.get(i).trim();
                    String value = schemaLocations.get(i + 1).trim();

                    for (String j : this.namespaceMapping.keySet()) {
                        Namespace namespace = this.namespaceMapping.get(j);

                        if (key.equalsIgnoreCase(namespace.getTargetNamespace())) {
                            namespace.setUrl(value);
                            break;
                        }
                    }
                }
            }
        }

        /*
         * 设置targetNamespace与url共享的namespace（schemaLocation中没有定义的）
         */
        for (String key : this.namespaceMapping.keySet()) {
            Namespace namespace = this.namespaceMapping.get(key);

            if (namespace.getUrl() == null) {
                namespace.setUrl(namespace.getTargetNamespace());
            }
        }
    }

    public Map<String, Namespace> getNamespaceMapping() {
        return namespaceMapping;
    }

    public Element getRootElement() {
        return rootElement;
    }

    public Map<String, String> getAttributes() {
        for (String key : this.namespaceMapping.keySet()) {
            Namespace namespace = this.namespaceMapping.get(key);

            if (XView.XVIEW_NAMESPACE.equalsIgnoreCase(namespace.getTargetNamespace())) {
                return Collections.unmodifiableMap(namespace.getAttribute());
            }
        }

        return Collections.emptyMap();
    }

    private static List<String> split(String s, String regex) {
        List<String> list = new ArrayList<>();

        for (String i : s.split(regex)) {
            if (!i.isEmpty()) {
                list.add(i);
            }
        }

        return list;
    }

    public static class Namespace {

        private String targetNamespace;

        private String url;

        private Map<String, String> attribute;

        public Namespace(String targetNamespace) {
            this.targetNamespace = targetNamespace;
            this.attribute = new HashMap<>();
        }

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public void setTargetNamespace(String targetNamespace) {
            this.targetNamespace = targetNamespace;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getAttribute() {
            return attribute;
        }
    }

    public static final int BUFFER_SIZE = 1024 * 4;

    public static byte[] readBytes(InputStream inputStream) throws IOException {
        int bufferSize = inputStream.available();
        if (bufferSize < 0) {
            bufferSize = BUFFER_SIZE;
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(bufferSize)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, length);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

    public static void buildXsd(File directory) {
        File[] xviewFiles = directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isFile() && pathname.getName().endsWith(XView.FILE_EXT)) {
                    return true;
                } else {
                    return false;
                }
            }
        });

        if (xviewFiles != null && xviewFiles.length > 0) {
            System.out.println("创建XSD文件：" + directory.getAbsolutePath());

            String targetNamespace = directory.getName();
            File xsdFile = new File(directory, targetNamespace + ".xsd");

            try (FileOutputStream fileOutputStream = new FileOutputStream(xsdFile, false)) {
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, "UTF-8")) {
                    outputStreamWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    outputStreamWriter.write("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" targetNamespace=\"" + targetNamespace + "\">\n");

                    for (int i = 0; i < xviewFiles.length; i++) {
                        try (FileInputStream fileInputStream = new FileInputStream(xviewFiles[i])) {
                            XViewXml xviewXml = new XViewXml(fileInputStream, false);
                            String xviewName = xviewFiles[i].getName().substring(0, xviewFiles[i].getName().lastIndexOf('.'));
                            StringBuilder stringBuilder = new StringBuilder();

                            stringBuilder.append("<element name=\"" + xviewName + "\">\n");
                            stringBuilder.append("<complexType mixed=\"true\">\n");
                            stringBuilder.append("<sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\n");
                            stringBuilder.append("<any processContents=\"lax\"/>\n");
                            stringBuilder.append("</sequence>\n");

                            Map<String, String> attributes = xviewXml.getAttributes();
                            for (String key : attributes.keySet()) {
                                String value = attributes.get(key);
                                value = value == null ? "" : value;
                                stringBuilder.append("<attribute name=\"" + key + "\" type=\"string\" default=\"" + value + "\"/>\n");
                            }

                            stringBuilder.append("</complexType>\n");
                            stringBuilder.append("</element>\n");

                            outputStreamWriter.write(stringBuilder.toString());

                        } catch (Exception e) {
                            System.out.println("无法访问XVIEW文件：" + xviewFiles[i].getAbsolutePath());
                            e.printStackTrace();
                        }
                    }

                    outputStreamWriter.write("</schema>");
                }
            } catch (Exception e) {
                System.out.println("无法创建XSD文件：" + xsdFile.getAbsolutePath());
                e.printStackTrace();
            }
        }

        directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    buildXsd(pathname);
                }

                return false;
            }
        });
    }

    public static void main(String[] args) {
        File file;

        if (args != null && args.length > 0) {
            file = new File(args[0]);
        } else {
            file = new File("");
        }

        System.out.println("扫描XVIEW文件并生成XSD文件：" + file.getAbsolutePath());
        buildXsd(file);
    }

}
