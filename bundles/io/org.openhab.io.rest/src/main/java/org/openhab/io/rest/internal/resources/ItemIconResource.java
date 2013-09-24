/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.io.rest.internal.resources;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openhab.io.rest.internal.resources.beans.ItemIconBean;
import org.openhab.io.rest.internal.resources.beans.ItemIconListBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



import com.sun.jersey.api.json.JSONWithPadding;

/**
 * <p>
 * This class acts as a REST resource for binding data and provides different
 * methods to interact with the, persistence store
 * 
 * <p>
 * The typical content types are plain text for status values and XML or JSON(P)
 * for more complex data structures
 * </p>
 * 
 * <p>
 * This resource is registered with the Jersey servlet.
 * </p>
 * 
 * @author Chris Jackson
 * @since 1.3.0
 */
@Path(ItemIconResource.PATH_BUNDLE)
public class ItemIconResource {

	private static final Logger	logger		= LoggerFactory.getLogger(ItemIconResource.class);

	/** The URI path to this resource */
	public static final String	PATH_BUNDLE	= "config/icons";

	@Context
	UriInfo						uriInfo;

	@GET
	@Produces({ MediaType.WILDCARD })
	public Response getIcons(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getIcons(uriInfo.getPath()), callback) : getIcons(uriInfo.getPath());
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	private static String getValue(Element elmnt, String name) {
		NodeList elmntLst = elmnt.getElementsByTagName(name);
		if (elmntLst == null)
			return "";

		if (((NodeList) elmntLst.item(0)).item(0) == null)
			return "";
		return ((Node) ((NodeList) elmntLst.item(0)).item(0)).getNodeValue();
	}
	
	private ItemIconListBean getIcons(String uriPath) {
		ItemIconListBean bean = new ItemIconListBean();

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		Document document = null;
		try {
			docBuilder = docBuilderFactory.newDocumentBuilder();
			// TODO: Hard coded path
			try {
				document = docBuilder.parse(new File("webapps/habmin/openhab/icons.xml"));
			} catch (SAXException e) {
				logger.error("Error reading XML file - ", e.toString());
			} catch (IOException e) {
				logger.error("Error reading XML file - ", e.toString());
			}
		} catch (ParserConfigurationException e) {
			logger.error("Error reading XML file - ", e.toString());
		}

		if (document == null)
			return null;

		// Normalise the XML
		document.getDocumentElement().normalize();

		// Get the binding information elements
		NodeList rootList = document.getElementsByTagName("iconset");
		Node rootNode = rootList.item(0);

		Element elmnt = (Element) rootNode;
		bean.name = getValue(elmnt, "name");
		bean.author = getValue(elmnt, "author");
		bean.description = getValue(elmnt, "description");
		bean.license = getValue(elmnt, "license");
//		bean.height = Integer.parseInt(getValue(elmnt, "height"));
//		bean.width = Integer.parseInt(getValue(elmnt, "height"));

		List<ItemIconBean> iconbeans = new LinkedList<ItemIconBean>();

		NodeList configList = document.getElementsByTagName("icon");
		for (int s = 0; s < configList.getLength(); s++) {
			ItemIconBean icon = new ItemIconBean();

			Node configNode = configList.item(s);
			elmnt = (Element) configNode;
			icon.name = getValue(elmnt, "name");
			icon.menuicon = getValue(elmnt, "menuicon");
			icon.label = getValue(elmnt, "label");
			icon.description = getValue(elmnt, "description");

			iconbeans.add(icon);
		}
		
		bean.entries.addAll(iconbeans);

		return bean;
	}

}
