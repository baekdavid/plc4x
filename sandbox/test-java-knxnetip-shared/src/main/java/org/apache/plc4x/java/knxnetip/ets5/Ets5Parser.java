/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.plc4x.java.knxnetip.ets5;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.plc4x.java.ets5.passive.KNXGroupAddress;
import org.apache.plc4x.java.ets5.passive.io.KNXGroupAddressIO;
import org.apache.plc4x.java.knxnetip.ets5.model.AddressType;
import org.apache.plc4x.java.knxnetip.ets5.model.Ets5Model;
import org.apache.plc4x.java.knxnetip.ets5.model.Function;
import org.apache.plc4x.java.knxnetip.ets5.model.GroupAddress;
import org.apache.plc4x.java.utils.ParseException;
import org.apache.plc4x.java.utils.ReadBuffer;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Ets5Parser {

    public Ets5Model parse(File knxprojFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();

            ZipFile zipFile = new ZipFile(knxprojFile);

            ////////////////////////////////////////////////////////////////////////////////
            // File containing the information on the type of encoding used for group addresses.
            ////////////////////////////////////////////////////////////////////////////////
            ZipArchiveEntry projectHeaderFile = zipFile.getEntry("P-05CD/project.xml");
            if(projectHeaderFile == null) {
                throw new RuntimeException("Error accessing project header file.");
            }
            Document projectHeaderDoc = builder.parse(zipFile.getInputStream(projectHeaderFile));
            final XPathExpression xpathGroupAddressStyle = xPath.compile("/KNX/Project/ProjectInformation/@GroupAddressStyle");
            Attr groupAddressStyle = (Attr) xpathGroupAddressStyle.evaluate(projectHeaderDoc, XPathConstants.NODE);
            byte groupAddressStyleCode = getGroupAddressLevel(groupAddressStyle.getTextContent());

            ////////////////////////////////////////////////////////////////////////////////
            // General information on the type of encoding and the value ranges.
            ////////////////////////////////////////////////////////////////////////////////
            ZipArchiveEntry knxMasterDataFile = zipFile.getEntry("knx_master.xml");
            if(knxMasterDataFile == null) {
                throw new RuntimeException("Error accessing KNX master file.");
            }
            Document knxMasterDoc = builder.parse(zipFile.getInputStream(knxMasterDataFile));
            final XPathExpression xpathDatapointSubtype = xPath.compile("//DatapointSubtype");
            NodeList datapointSubtypeNodes = (NodeList) xpathDatapointSubtype.evaluate(knxMasterDoc, XPathConstants.NODESET);
            Map<String, AddressType> addressTypes = new HashMap<>();
            for(int i = 0; i < datapointSubtypeNodes.getLength(); i++) {
                final Element datapointSubtypeNode = (Element) datapointSubtypeNodes.item(i);
                final String id = datapointSubtypeNode.getAttribute("Id");
                final int subType = Integer.parseInt(datapointSubtypeNode.getAttribute("Number"));
                final int mainType = Integer.parseInt(
                    ((Element) datapointSubtypeNode.getParentNode().getParentNode()).getAttribute("Number"));
                final String name = datapointSubtypeNode.getAttribute("Text");
                addressTypes.put(id, new AddressType(id, mainType, subType, name));
            }

            ////////////////////////////////////////////////////////////////////////////////
            // File containing all the information about group addresses used, their names, types etc.
            ////////////////////////////////////////////////////////////////////////////////
            ZipArchiveEntry projectFile = zipFile.getEntry("P-05CD/0.xml");
            if(projectFile == null) {
                throw new RuntimeException("Error accessing project file.");
            }
            Document projectDoc = builder.parse(zipFile.getInputStream(projectFile));

            final Map<String, Function> groupAddressRefs = new HashMap();
            final XPathExpression xpathGroupAddressRef = xPath.compile("//GroupAddressRef");
            NodeList groupAddressRefNodes = (NodeList) xpathGroupAddressRef.evaluate(projectDoc, XPathConstants.NODESET);
            for(int i = 0; i < groupAddressRefNodes.getLength(); i++) {
                final Element groupAddressRefNode = (Element) groupAddressRefNodes.item(i);
                final String refId = groupAddressRefNode.getAttribute("RefId");
                final Element functionNode = (Element) groupAddressRefNode.getParentNode();
                final String functionName = functionNode.getAttribute("Name");
                // Function Type information is stored in knx_master.xml (//FunctionType[@id='functionTypeId']
                final String functionTypeId = functionNode.getAttribute("Type");
                final Element spaceNode = (Element) functionNode.getParentNode();
                final String spaceName = spaceNode.getAttribute("Name");

                final Function function = new Function(refId, functionName, functionTypeId, spaceName);
                if(groupAddressRefs.containsKey(refId)) {
                    System.out.println("Duplicate refId");
                }
                groupAddressRefs.put(refId, function);
            }

            final XPathExpression xpathGroupAddresses = xPath.compile("//GroupAddress");
            NodeList groupAddressNodes = (NodeList) xpathGroupAddresses.evaluate(projectDoc, XPathConstants.NODESET);
            Map<KNXGroupAddress, GroupAddress> groupAddresses = new HashMap<>();
            for(int i = 0; i < groupAddressNodes.getLength(); i++) {
                final Element groupAddressNode = (Element) groupAddressNodes.item(i);

                final String id = groupAddressNode.getAttribute("Id");
                final Function function = groupAddressRefs.get(id);

                final int addressInt = Integer.parseInt(groupAddressNode.getAttribute("Address"));
                final byte[] addressBytes = new byte[2];
                addressBytes[0] = (byte) ((addressInt >> 8) & 0xFF);
                addressBytes[1] = (byte) (addressInt & 0xFF);
                ReadBuffer readBuffer = new ReadBuffer(addressBytes);
                final KNXGroupAddress knxGroupAddress = KNXGroupAddressIO.parse(readBuffer, groupAddressStyleCode);

                final String name = groupAddressNode.getAttribute("Name");

                final String typeString = groupAddressNode.getAttribute("DatapointType");
                final AddressType addressType = addressTypes.get(typeString);

                GroupAddress groupAddress = new GroupAddress(knxGroupAddress, name, addressType, function);
                groupAddresses.put(knxGroupAddress, groupAddress);
            }

            return new Ets5Model(groupAddressStyleCode, groupAddresses);
        } catch (IOException e) {
            // Zip and Xml Stuff
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            // XML Stuff
            e.printStackTrace();
        } catch (SAXException e) {
            // XML Stuff
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            // XML Stuff
            e.printStackTrace();
        } catch (ParseException e) {
            // KNXNet/IP Parser Stuff
            e.printStackTrace();
        }
        return null;
    }

    private byte getGroupAddressLevel(String knxprojValue) {
        if("ThreeLevel".equals(knxprojValue)) {
            return (byte) 3;
        } else if("TwoLevel".equals(knxprojValue)) {
            return (byte) 2;
        } else if("Free".equals(knxprojValue)) {
            return (byte) 1;
        }
        throw new RuntimeException("Unsupported GroupAddressStyle=" + knxprojValue);
    }

    public static void main(String[] args) {
        final Ets5Model model = new Ets5Parser().parse(new File("/Users/christofer.dutz/Projects/Apache/PLC4X-Documents/KNX/Stettiner Str. 13/Stettiner Str.knxproj"));
        System.out.println(model);
    }

}
