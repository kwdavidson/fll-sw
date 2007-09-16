/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.xml;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import fll.Utilities;

/**
 * Parse challenge description and generate script/text for scoreEntry page.
 * 
 * @version $Revision$
 */
public final class ChallengeParser {

  /**
   * The expected namespace for FLL documents
   */
  public static final String FLL_NAMESPACE = "http://www.hightechkids.org";

  private static final Logger LOG = Logger.getLogger(ChallengeParser.class);

  /**
   * Just for debugging.
   * 
   * @param args
   *          ignored
   */
  /**
   * @param args
   */
  public static void main(final String[] args) {
    try {
      // final ClassLoader classLoader = ChallengeParser.class.getClassLoader();
      final java.io.FileReader input = new java.io.FileReader(
          "c:/Documents and Settings/jpschewe/projects/fll-sw/working-dir/challenge-descriptors/challenge-hsr-2006.xml");
      final Document challengeDocument = ChallengeParser.parse(input);
      if(null == challengeDocument) {
        throw new RuntimeException("Error parsing challenge.xml");
      }

      LOG.info("Title: " + challengeDocument.getDocumentElement().getAttribute("title"));
      final org.w3c.dom.Element rootElement = challengeDocument.getDocumentElement();
      final org.w3c.dom.Element performanceElement = (org.w3c.dom.Element)rootElement.getElementsByTagName("Performance").item(0);
      final org.w3c.dom.NodeList goals = performanceElement.getElementsByTagName("goal");
      for(int i = 0; i < goals.getLength(); i++) {
        final Element element = (org.w3c.dom.Element)goals.item(i);
        final String name = element.getAttribute("name");
        LOG.info("The min value for goal " + name + " is " + element.getAttribute("min"));
      }

      LOG.info("Document");
      final XMLWriter xmlwriter = new XMLWriter();
      xmlwriter.setOutput(new java.io.PrintWriter(System.out));
      xmlwriter.write(challengeDocument);

    } catch(final Exception e) {
      e.printStackTrace();
    }
  }

  private ChallengeParser() {
  }

  /**
   * Parse the challenge document from the given stream. The document will be
   * validated and must be in the fll namespace. Does not close the stream after
   * reading.
   * 
   * @param stream
   *          a stream containing document
   * @return the challengeDocument, null on an error
   */
  public static Document parse(final Reader stream) {
    try {
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      final Source schemaFile = new StreamSource(classLoader.getResourceAsStream("fll/resources/fll.xsd"));
      final Schema schema = factory.newSchema(schemaFile);

      final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      builderFactory.setNamespaceAware(true);
      builderFactory.setSchema(schema);
      builderFactory.setIgnoringComments(true);
      builderFactory.setIgnoringElementContentWhitespace(true);
      final DocumentBuilder parser = builderFactory.newDocumentBuilder();

      parser.setErrorHandler(new ErrorHandler() {
        public void error(final SAXParseException spe) throws SAXParseException {
          throw spe;
        }

        public void fatalError(final SAXParseException spe) throws SAXParseException {
          throw spe;
        }

        public void warning(final SAXParseException spe) throws SAXParseException {
          System.err.println(spe.getMessage());
        }
      });

      parser.setEntityResolver(new EntityResolver() {
        public InputSource resolveEntity(final String publicID, final String systemID) throws SAXException, IOException {
          if(LOG.isDebugEnabled()) {
            LOG.debug("resolveEntity(" + publicID + ", " + systemID + ")");
          }
          if(systemID.endsWith("fll.xsd")) {
            // just use the one we store internally
            // final int slashidx = systemID.lastIndexOf("/") + 1;
            return new InputSource(classLoader.getResourceAsStream("fll/resources/fll.xsd")); // +
            // systemID.substring(slashidx)));
          } else {
            return null;
          }
        }
      });

      // pull the whole stream into a string
      final StringWriter writer = new StringWriter();
      final char[] buffer = new char[1024];
      int bytesRead;
      while((bytesRead = stream.read(buffer)) != -1) {
        writer.write(buffer, 0, bytesRead);
      }

      final Document document = parser.parse(new InputSource(new StringReader(writer.toString())));
      validateDocument(document);
      return document;
    } catch(final SAXParseException spe) {
      throw new RuntimeException("Error parsing file line: " + spe.getLineNumber() + " column: " + spe.getColumnNumber() + " " + spe.getMessage());
    } catch(final SAXException se) {
      throw new RuntimeException(se);
    } catch(final IOException ioe) {
      throw new RuntimeException(ioe);
    } catch(final ParserConfigurationException pce) {
      throw new RuntimeException(pce);
    } catch(final ParseException pe) {
      throw new RuntimeException(pe);
    }
  }

  /**
   * Do validation of the document that cannot be done by the XML parser.
   * 
   * @param document
   *          the document to validate
   * @throws ParseException
   * @throws RuntimeException
   *           if an error occurs
   */
  private static void validateDocument(final Document document) throws ParseException {
    final Element rootElement = document.getDocumentElement();
    if(!"fll".equals(rootElement.getTagName())) {
      throw new RuntimeException("Not a fll challenge description file");
    }

    final NodeList children = rootElement.getChildNodes();
    for(int childIdx = 0; childIdx < children.getLength(); ++childIdx) {
      final Node childNode = children.item(childIdx);
      if("Performance".equals(childNode.getNodeName()) || "SubjectiveCategory".equals(childNode.getNodeName())) {
        final Element childElement = (Element)childNode;

        // get all nodes named goal at any level under category element
        final NodeList goalNodes = childElement.getElementsByTagName("goal");
        final Map<String, Element> goals = new HashMap<String, Element>();
        for(int i = 0; i < goalNodes.getLength(); ++i) {
          final Element element = (Element)goalNodes.item(i);
          final String name = element.getAttribute("name");
          goals.put(name, element);

          // check initial values
          final int initialValue = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("initialValue")).intValue();
          if(XMLUtils.isEnumeratedGoal(element)) {
            boolean foundMatch = false;
            final NodeList values = element.getChildNodes();
            for(int valueIdx = 0; valueIdx < values.getLength(); ++valueIdx) {
              final Element valueEle = (Element)values.item(valueIdx);
              final int score = Utilities.NUMBER_FORMAT_INSTANCE.parse(valueEle.getAttribute("score")).intValue();
              if(score == initialValue) {
                foundMatch = true;
              }
            }
            if(!foundMatch) {
              throw new RuntimeException(new Formatter().format("Initial value for %s(%d) does not match the score of any value element within the goal", name, initialValue).toString());
            }

          } else {
            final int min = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("min")).intValue();
            final int max = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("max")).intValue();
            if(initialValue < min) {
              throw new RuntimeException(new Formatter().format("Initial value for %s(%d) is less than min(%d)", name, initialValue, min).toString());
            }
            if(initialValue > max) {
              throw new RuntimeException(new Formatter().format("Initial value for %s(%d) is greater than max(%d)", name, initialValue, max)
                  .toString());
            }
          }
        }

        // for all computedGoals
        final NodeList computedGoalNodes = childElement.getElementsByTagName("computedGoal");
        for(int i = 0; i < computedGoalNodes.getLength(); ++i) {
          final Element computedGoalElement = (Element)computedGoalNodes.item(i);

          // for all termElements
          final NodeList termElements = computedGoalElement.getElementsByTagName("term");
          for(int termIndex = 0; termIndex < termElements.getLength(); ++termIndex) {
            final Element termElement = (Element)termElements.item(termIndex);

            // check that the computed goal only references goals
            final String referencedGoalName = termElement.getAttribute("goal");
            if(!goals.containsKey(referencedGoalName)) {
              throw new RuntimeException("Computed goal '" + computedGoalElement.getAttribute("name") + "' references goal '" + referencedGoalName
                  + "' which is not a standard goal");
            }
          }

          // for all goalRef elements
          final NodeList goalRefElements = computedGoalElement.getElementsByTagName("goalRef");
          for(int goalRefIndex = 0; goalRefIndex < goalRefElements.getLength(); ++goalRefIndex) {
            final Element goalRefElement = (Element)goalRefElements.item(goalRefIndex);

            // can't reference a non-enum goal with goalRef in enumCond
            final String referencedGoalName = goalRefElement.getAttribute("goal");
            final Element referencedGoalElement = goals.get(referencedGoalName);
            if(!XMLUtils.isEnumeratedGoal(referencedGoalElement)) {
              throw new RuntimeException("Computed goal '" + computedGoalElement.getAttribute("name")
                  + "' has a goalRef element that references goal '" + referencedGoalName + " " + referencedGoalElement
                  + "' which is not an enumerated goal");
            }
          }

        } // end foreach computed goal

        // for all terms
        final NodeList termElements = childElement.getElementsByTagName("term");
        for(int termIndex = 0; termIndex < termElements.getLength(); ++termIndex) {
          final Element termElement = (Element)termElements.item(termIndex);
          final String goalValueType = termElement.getAttribute("scoreType");
          final String referencedGoalName = termElement.getAttribute("goal");
          final Element referencedGoalElement = goals.get(referencedGoalName);
          // can't use the raw score of an enum inside a polynomial term
          if("raw".equals(goalValueType) && (XMLUtils.isEnumeratedGoal(referencedGoalElement) || XMLUtils.isComputedGoal(referencedGoalElement))) {
            throw new RuntimeException("Cannot use the raw score from an enumerated or computed goal in a polynomial term.  Referenced goal '"
                + referencedGoalName + "'");
          }
        }

      } // end if child node (performance or subjective)
    } // end foreach child node
  } // end validateDocument
}