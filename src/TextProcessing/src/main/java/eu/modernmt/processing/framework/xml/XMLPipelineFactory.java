package eu.modernmt.processing.framework.xml;

import eu.modernmt.processing.framework.PipelineFactory;
import eu.modernmt.processing.framework.ProcessingException;
import eu.modernmt.processing.framework.ProcessingPipeline;
import eu.modernmt.processing.framework.TextProcessor;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by davide on 31/05/16.
 */
public class XMLPipelineFactory<P, R> extends PipelineFactory<P, R> {

    private final List<IProcessorFactory> factories;
    private final Class<ProcessingPipeline> pipelineClass;

    private XMLPipelineFactory(Class<ProcessingPipeline> pipelineClass, List<IProcessorFactory> factories) {
        this.pipelineClass = pipelineClass;
        this.factories = factories;
    }

    public static <P, R> XMLPipelineFactory<P, R> loadFromXML(File file) throws IOException, ProcessingException {
        FileInputStream input = null;

        try {
            input = new FileInputStream(file);
            return loadFromXML(input);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }

    @SuppressWarnings("unchecked")
    public static <P, R> XMLPipelineFactory<P, R> loadFromXML(InputStream input) throws IOException, ProcessingException {
        Document xml = getXMLDocument(input);
        Element pipeline = xml.getDocumentElement();

        // Pipeline Class
        Class<ProcessingPipeline> pipelineClass = ProcessingPipeline.class;
        if (pipeline.hasAttribute("class")) {
            String className = pipeline.getAttribute("class").trim();
            try {
                pipelineClass = (Class<ProcessingPipeline>) Class.forName(className);
            } catch (ClassCastException | ClassNotFoundException e) {
                throw new ProcessingException("Invalid pipeline class " + className, e);
            }
        }

        ArrayList<IProcessorFactory> factories = new ArrayList<>();

        NodeList processors = pipeline.getChildNodes();
        for (int i = 0; i < processors.getLength(); i++) {
            Node node = processors.item(i);
            if (node instanceof Element)
                factories.add(parseNode((Element) node));
        }

        return new XMLPipelineFactory(pipelineClass, factories);
    }

    private static Document getXMLDocument(InputStream input) throws ProcessingException, IOException {
        String packageName = ProcessingPipeline.class.getPackage().getName().replace('.', '/');
        String xsdPath = packageName + "/pipeline-schema.xsd";

        Document xml;
        InputStream xsdResource = null;

        try {
            xsdResource = ProcessingPipeline.class.getClassLoader().getResourceAsStream(xsdPath);
            if (xsdResource == null)
                throw new ProcessingException("Unable to load XSD " + xsdPath);

            // Load XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            xml = builder.parse(input);

            // Optional, but recommended
            // read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            xml.getDocumentElement().normalize();

            // Validate XML
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdResource));
            Validator validator = schema.newValidator();
            validator.validate(new DOMSource(xml));
        } catch (ParserConfigurationException e) {
            throw new Error("Unable to instantiate XML Document Factory", e);
        } catch (SAXException e) {
            throw new ProcessingException(e);
        } finally {
            IOUtils.closeQuietly(xsdResource);
        }

        return xml;
    }

    private static IProcessorFactory parseNode(Element node) {
        String tag = node.getTagName();

        if ("processor".equals(tag)) {
            return getProcessorNode(node);
        } else if ("processorGroup".equals(tag)) {
            ArrayList<XMLProcessorFactory> factories = new ArrayList<>();

            NodeList nodes = node.getElementsByTagName("processor");
            for (int i = 0; i < nodes.getLength(); i++)
                factories.add(getProcessorNode((Element) nodes.item(i)));

            return new XMLProcessorGroupFactory(factories);
        }

        throw new Error("This should never happen");
    }

    private static XMLProcessorFactory getProcessorNode(Element node) {
        String sourceAttribute = node.hasAttribute("source") ? node.getAttribute("source") : null;
        String targetAttribute = node.hasAttribute("target") ? node.getAttribute("target") : null;
        String className = node.getTextContent().trim();

        if (sourceAttribute == null && targetAttribute == null)
            return new XMLProcessorFactory(className);
        else
            return new XMLFilteredProcessorFactory(className, sourceAttribute, targetAttribute);
    }

    @SuppressWarnings("unchecked")
    public ProcessingPipeline<P, R> newPipeline(Locale source, Locale target) throws ProcessingException {
        ArrayList<TextProcessor> processors = new ArrayList<>(factories.size());

        for (IProcessorFactory factory : factories) {
            if (factory.accept(source, target))
                processors.add(factory.create(source, target));
        }

        try {
            return (ProcessingPipeline<P, R>) pipelineClass.getConstructor(List.class).newInstance(processors);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new ProcessingException("Failed to instantiate class " + pipelineClass, e);
        }
    }

}
