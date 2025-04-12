package au.com.interlated.doctoarff;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class DocToArff {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java DocToArff <wordFolderPath> <arffFilePath>");
            System.exit(1);
        }

        String wordFolderPath = args[0];
        String arffFilePath = args[1];

        try {
            convertWordFolderToSingleArff(wordFolderPath, arffFilePath);
            System.out.println("Conversion of Word documents to a single ARFF file successful!");
        } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void convertWordFolderToSingleArff(String wordFolderPath, String arffFilePath)
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {

        File folder = new File(wordFolderPath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles == null) {
            throw new IOException("Invalid folder path or folder is empty: " + wordFolderPath);
        }

        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("paragraph_text", (ArrayList<String>) null)); // String attribute for text
        // Give the ARFF file a meaningful name
        Instances data = new Instances("WordDocumentsFrom_" + new File(wordFolderPath).getName(), attributes, 0); // 0 initial capacity

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arffFilePath, StandardCharsets.UTF_8))) {

            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".docx")) {
                    String wordFilePath = file.getAbsolutePath();
                    System.out.println("Processing file: " + wordFilePath);

                    try (FileInputStream fis = new FileInputStream(wordFilePath)) {
                        ZipSecureFile.setMinInflateRatio(0.005d);
                        XWPFDocument document = new XWPFDocument(fis);
                        List<XWPFParagraph> paragraphs = document.getParagraphs();

                        for (XWPFParagraph paragraph : paragraphs) {
                            String paragraphText = getParagraphText(paragraph);
                            double[] values = new double[1];
                            values[0] = data.attribute(0).addStringValue(paragraphText);
                            data.add(new DenseInstance(1.0, values));
                        }
                    } catch (FileNotFoundException e) {
                        System.err.println("Word file not found: " + wordFilePath + ". Skipping.");
                        continue;
                    } catch (org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
                        System.err.println("Error processing file: " + wordFilePath + ".  Skipping due to: " + e.getMessage());
                        e.printStackTrace();
                        continue;
                    } catch (IOException e) {
                        System.err.println("Error processing file: " + wordFilePath + ".  Skipping due to: " + e.getMessage());
                        e.printStackTrace();
                        continue;
                    }
                }
            }
            writer.write(data.toString());
        }
    }

    private static String getParagraphText(XWPFParagraph paragraph)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        StringBuilder text = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            if (run != null && run.getText(0) != null) {
                text.append(run.getText(0));
            }
        }

        if (paragraph.getCTP() != null) {
            String paragraphXML = paragraph.getCTP().xmlText();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document xmlDoc = builder.parse(new InputSource(new ByteArrayInputStream(paragraphXML.getBytes(StandardCharsets.UTF_8))));
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            NodeList embeddedTextNodes = (NodeList) xpath.evaluate("//text()", xmlDoc, XPathConstants.NODESET);
            for (int i = 0; i < embeddedTextNodes.getLength(); i++) {
                Node node = embeddedTextNodes.item(i);
                String nodeValue = node.getNodeValue();
                if (nodeValue != null) {
                    text.append(nodeValue.trim());
                }
            }
        }
        return text.toString().trim();
    }
}

