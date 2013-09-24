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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.atmosphere.annotation.Suspend.SCOPE;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.jersey.SuspendResponse;
import org.openhab.io.rest.internal.broadcaster.GeneralBroadcaster;
import org.openhab.io.rest.internal.resources.beans.BindingBean;
import org.openhab.io.rest.internal.resources.beans.BindingListBean;
import org.openhab.io.rest.internal.resources.beans.BindingConfigBean;
import org.openhab.io.rest.internal.resources.beans.BindingConfigListBean;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
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
@Path(BindingConfigResource.PATH_BUNDLE)
public class BindingConfigResource {

	private static final Logger	logger		= LoggerFactory.getLogger(BindingConfigResource.class);

	/** The URI path to this resource */
	public static final String	PATH_BUNDLE	= "config/bindings";

	@Context
	UriInfo						uriInfo;

	@GET
	@Produces({ MediaType.WILDCARD })
	public Response getBindings(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new BindingListBean(getBindings(uriInfo.getPath())), callback) : new BindingListBean(
					getBindings(uriInfo.getPath()));
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/{bindingname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public SuspendResponse<Response> getBindingData(@Context HttpHeaders headers,
			@PathParam("bindingname") String bindingname, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			@HeaderParam(HeaderConfig.X_ATMOSPHERE_TRANSPORT) String atmosphereTransport,
			@Context AtmosphereResource resource) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		if (atmosphereTransport == null || atmosphereTransport.isEmpty()) {
			final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
			if (responseType != null) {
				final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
						getBinding(uriInfo.getPath(), bindingname), callback) : getBinding(uriInfo.getPath(),
						bindingname);
				throw new WebApplicationException(Response.ok(responseObject, responseType).build());
			} else {
				throw new WebApplicationException(Response.notAcceptable(null).build());
			}
		}
		GeneralBroadcaster itemBroadcaster = (GeneralBroadcaster) BroadcasterFactory.getDefault().lookup(
				GeneralBroadcaster.class, resource.getRequest().getPathInfo(), true);
		return new SuspendResponse.SuspendResponseBuilder<Response>().scope(SCOPE.REQUEST)
				.resumeOnBroadcast(!ResponseTypeHelper.isStreamingTransport(resource.getRequest()))
				.broadcaster(itemBroadcaster).outputComments(true).build();
	}

	@PUT
	@Path("/{bindingname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	@Consumes({ MediaType.APPLICATION_JSON })
	public SuspendResponse<Response> putBindingData(@Context HttpHeaders headers,
			@PathParam("bindingname") String bindingname, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			@HeaderParam(HeaderConfig.X_ATMOSPHERE_TRANSPORT) String atmosphereTransport,
			@Context AtmosphereResource resource, BindingConfigListBean bindingData) {
		logger.debug("Received HTTP PUT request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		if (atmosphereTransport == null || atmosphereTransport.isEmpty()) {
			final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
			if (responseType != null) {
				final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
						putBinding(uriInfo.getPath(), bindingname, bindingData), callback) : putBinding(uriInfo.getPath(), bindingname, bindingData);
				throw new WebApplicationException(Response.ok(responseObject, responseType).build());
			} else {
				throw new WebApplicationException(Response.notAcceptable(null).build());
			}
		}
		GeneralBroadcaster itemBroadcaster = (GeneralBroadcaster) BroadcasterFactory.getDefault().lookup(
				GeneralBroadcaster.class, resource.getRequest().getPathInfo(), true);
		return new SuspendResponse.SuspendResponseBuilder<Response>().scope(SCOPE.REQUEST)
				.resumeOnBroadcast(!ResponseTypeHelper.isStreamingTransport(resource.getRequest()))
				.broadcaster(itemBroadcaster).outputComments(true).build();
	}

	private static String getValue(Element elmnt, String name) {
		NodeList elmntLst = elmnt.getElementsByTagName(name);
		if (elmntLst == null)
			return null;

		if (((NodeList) elmntLst.item(0)).item(0) == null)
			return null;
		return ((Node) ((NodeList) elmntLst.item(0)).item(0)).getNodeValue();
	}

	public static List<BindingConfigBean> getBindingConfig(Document document, String tag) {
		List<BindingConfigBean> configbeans = new LinkedList<BindingConfigBean>();

		NodeList configList = document.getElementsByTagName(tag);
		for (int s = 0; s < configList.getLength(); s++) {
			BindingConfigBean configbean = new BindingConfigBean();

			Node configNode = configList.item(s);
			Element elmnt = (Element) configNode;
			configbean.name = getValue(elmnt, "name");
			configbean.label = getValue(elmnt, "label");
			configbean.description = getValue(elmnt, "description");
			configbean.optional = Boolean.parseBoolean(getValue(elmnt, "optional"));
			configbean.def = getValue(elmnt, "default");
			configbean.minimum = getValue(elmnt, "minimum");
			configbean.maximum = getValue(elmnt, "maximum");

			configbeans.add(configbean);
		}

		return configbeans;
	}

	private static Map<String, String> configFileGetProperties(File configFile, String pid) throws IOException,
			FileNotFoundException {
		// also cache the already retrieved configurations for each pid
		Map<String, String> configMap = new HashMap<String, String>();

		List<String> linesIn = IOUtils.readLines(new FileInputStream(configFile));
		for (String line : linesIn) {
			String[] contents = parseLine(configFile.getPath(), line, false);
			// no valid configuration line, so continue
			if (contents == null)
				continue;

			if (pid.equals(contents[0])) {
				configMap.put(contents[1], contents[2]);
			}
		}

		return configMap;
	}

	private static String[] parseLine(final String filePath, final String line, boolean checkComments) {
		String trimmedLine = line.trim();

		if(checkComments == false) {
			if (trimmedLine.startsWith("#") || trimmedLine.isEmpty()) {
				return null;
			}
		}
		else {	
			if(trimmedLine.startsWith("#"))
				trimmedLine = trimmedLine.substring(1);
			trimmedLine = trimmedLine.trim();
			if (trimmedLine.isEmpty()) {
				return null;
			}
		}

		if (trimmedLine.substring(1).contains(":")) {
			String pid = StringUtils.substringBefore(trimmedLine, ":");
			String rest = trimmedLine.substring(pid.length() + 1);
			if (!rest.isEmpty() && rest.substring(1).contains("=")) {
				String property = StringUtils.substringBefore(rest, "=");
				String value = rest.substring(property.length() + 1);
				return new String[] { pid.trim(), property.trim(), value.trim() };
			}
		}

		return null;
	}

	public static BindingBean createBindingBean(Bundle bundle, String uriPath, boolean detail) {
		BindingBean bean = new BindingBean();
		bean.bundle = bundle.getSymbolicName();
		bean.osgiVersion = bundle.getVersion().toString();

		String pid = bean.bundle.substring(bean.bundle.lastIndexOf('.') + 1);

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		Document document = null;
		try {
			docBuilder = docBuilderFactory.newDocumentBuilder();
			// TODO: Hard coded path
			try {
				document = docBuilder.parse(new File("webapps/habmin/openhab/" + pid + ".xml"));
			} catch (SAXException e) {
				logger.error("Error reading XML file - ", e.toString());
			} catch (IOException e) {
				logger.error("Error reading XML file - ", e.toString());
			}
		} catch (ParserConfigurationException e) {
			logger.error("Error reading XML file - ", e.toString());
		}

		if (document == null)
			return bean;

		// Normalise the XML
		document.getDocumentElement().normalize();

		// Get the binding information elements
		NodeList rootList = document.getElementsByTagName("binding");
		Node rootNode = rootList.item(0);

		Element elmnt = (Element) rootNode;
		bean.name = getValue(elmnt, "name");
		bean.author = getValue(elmnt, "author");
		bean.pid = getValue(elmnt, "pid");
		bean.type = getValue(elmnt, "type");
		bean.version = getValue(elmnt, "version");
		bean.ohVersion = getValue(elmnt, "oh_version");
		bean.link = uriPath + getValue(elmnt, "pid");

		if (detail == false)
			return bean;

		List<BindingConfigBean> generalConfig = getBindingConfig(document, "config.setting");
		List<BindingConfigBean> interfaceConfig = getBindingConfig(document, "interface.setting");

		File configFile = new File("configurations/openhab.cfg");

		try {
			Map<String, String> config = configFileGetProperties(configFile, pid);

			boolean found = false;
			for (Entry<String, String> entry : config.entrySet()) {
				found = false;
				for (BindingConfigBean bn : generalConfig) {
					if (bn.name.equals(entry.getKey())) {
						bn.value = entry.getValue();
						found = true;
						break;
					}
				}

				if (found == false) {
					String parts[] = entry.getKey().split("\\.");

					if (parts.length == 2) {
						for (BindingConfigBean bn : interfaceConfig) {
							BindingConfigBean configbean = new BindingConfigBean();
							configbean.copy(bn);
							configbean.label = parts[0] + ": " + bn.label;
							configbean.name = parts[0] + '.' + bn.name;

							if (configbean.name.equals(entry.getKey())) {
								configbean.value = entry.getValue();
							}
							generalConfig.add(configbean);
						}
					}
				}
			}
		} catch (IOException e) {
			logger.debug("Error reading config file: " + e);
		}

		bean.generalconfig = generalConfig;
		bean.interfaceconfig = interfaceConfig;

		return bean;
	}

	private List<BindingBean> getBindings(String uriPath) {
		List<BindingBean> beans = new LinkedList<BindingBean>();

		BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

		for (Bundle bundle : bundleContext.getBundles()) {
			BindingBean bean = null;
			String name = bundle.getSymbolicName();
			if (name.startsWith("org.openhab.binding"))
				bean = (BindingBean) createBindingBean(bundle, uriPath, false);
			if (name.startsWith("org.openhab.action"))
				bean = (BindingBean) createBindingBean(bundle, uriPath, false);
			if (name.startsWith("org.openhab.persistence"))
				bean = (BindingBean) createBindingBean(bundle, uriPath, false);

			if (bean != null)
				beans.add(bean);
		}
		return beans;
	}

	private BindingBean getBinding(String uriPath, String bundleName) {
		BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

		for (Bundle bundle : bundleContext.getBundles()) {
			BindingBean bean = null;
			String name = bundle.getSymbolicName();
			name = name.substring(name.lastIndexOf('.') + 1);
			if (name.equals(bundleName)) {
				bean = (BindingBean) createBindingBean(bundle, uriPath, true);

				if (bean != null)
					return bean;
			}
		}
		return null;
	}

	private BindingBean putBinding(String uriPath, String bundle, BindingConfigListBean properties) {
		if(properties == null)
			return null;

		File configFile = new File("configurations/openhab.cfg");
		try {
			// Read all the lines in the config file
			List<String> linesIn = IOUtils.readLines(new FileInputStream(configFile));

			// Scan the config file to find the end of the relevant section
			int sectionEndLine = -1;
			for (int cnt = 0; cnt < linesIn.size(); cnt++) {
				String[] contents = parseLine(configFile.getPath(), linesIn.get(cnt), true);
				
				if(contents == null)
					continue;

				if (properties.pid.equals(contents[0]))
					sectionEndLine = cnt+1;
			}

			// Loop through the config file looking for all the lines for this binding
			for(BindingConfigBean config : properties.entries) {
				boolean found = false;

				for (int cnt = 0; cnt < linesIn.size(); cnt++) {
					String[] contents = parseLine(configFile.getPath(), linesIn.get(cnt), true);
					// no valid configuration line, so continue
					if (contents == null)
						continue;
					
					if (!properties.pid.equals(contents[0]))
						continue;
				
					if(!config.name.equals(contents[1]))
						continue;
					
					// Found a config setting
					if(config.value.isEmpty()) {
						// If this is an interface setting (has a . in the parameter) then just delete it
						if(contents[1].contains(".")) {
							linesIn.remove(cnt);
							if(sectionEndLine != -1)
								sectionEndLine--;
						}
						else
							linesIn.set(cnt, "#" + contents[0] + ":" + contents[1] + " = " + contents[2]);
					}
					else
						linesIn.set(cnt, contents[0] + ":" + contents[1] + " = " + config.value);
					
					found = true;
					break;
				}
				
				// Did we find the setting in the file?
				if(found == false && !config.value.isEmpty()) {
					// Nope!
					// Add it to the end of the section
					if(sectionEndLine == -1) {
						linesIn.add("");
						linesIn.add(properties.pid + ":" + config.name + " = " + config.value);
					}
					else {
						linesIn.add(sectionEndLine++, "");
						linesIn.add(sectionEndLine++, properties.pid + ":" + config.name + " = " + config.value);
					}
				}
			}
			
			// Everything is updated - just write the file to disk!
			FileWriter fw = new FileWriter(configFile, false);
			BufferedWriter out = new BufferedWriter(fw);

			IOUtils.writeLines(linesIn, "\r\n", out);

			out.close();

		} catch (IOException e) {
			logger.debug("Error reading config file: " + e);
		}

		return getBinding(uriPath, bundle);
	}

}
