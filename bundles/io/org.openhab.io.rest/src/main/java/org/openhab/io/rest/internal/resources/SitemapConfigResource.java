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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.atmosphere.cpr.Broadcaster;
import org.eclipse.emf.common.util.EList;
import org.openhab.core.items.Item;
import org.openhab.io.rest.internal.RESTApplication;
import org.openhab.io.rest.internal.resources.beans.MappingBean;
import org.openhab.io.rest.internal.resources.beans.PageBean;
import org.openhab.io.rest.internal.resources.beans.SitemapBean;
import org.openhab.io.rest.internal.resources.beans.SitemapListBean;
import org.openhab.io.rest.internal.resources.beans.WidgetConfigBean;
import org.openhab.model.core.ModelRepository;
import org.openhab.model.sitemap.Chart;
import org.openhab.model.sitemap.Image;
import org.openhab.model.sitemap.LinkableWidget;
import org.openhab.model.sitemap.Mapping;
import org.openhab.model.sitemap.Selection;
import org.openhab.model.sitemap.Setpoint;
import org.openhab.model.sitemap.Sitemap;
import org.openhab.model.sitemap.Slider;
import org.openhab.model.sitemap.Switch;
import org.openhab.model.sitemap.Video;
import org.openhab.model.sitemap.Webview;
import org.openhab.model.sitemap.Widget;
import org.openhab.ui.items.ItemUIRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.json.JSONWithPadding;

/**
 * <p>
 * This class acts as a REST resource for history data and provides different
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
@Path(SitemapConfigResource.PATH_CONFIG)
public class SitemapConfigResource {

	private static final Logger		logger				= LoggerFactory.getLogger(SitemapConfigResource.class);

	private static final Pattern	SITEMAP_DEFINITION	= Pattern
																.compile(".*?sitemap (.*?) label\\s*=\\s*[\"|'](.*?)[\"|']");

	protected static final String	SITEMAP_FILEEXT		= ".sitemap";

	public static final String		PATH_SITEMAPS		= "sitemaps";

	/** The URI path to this resource */
	public static final String		PATH_CONFIG			= "config/sitemap";

	@Context
	UriInfo							uriInfo;
	@Context
	Broadcaster						sitemapBroadcaster;

	@GET
	@Produces({ MediaType.WILDCARD })
	public Response getSitemaps(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", new String[] { uriInfo.getPath(), type });
		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new SitemapListBean(getSitemapBeans(uriInfo.getAbsolutePathBuilder().build())), callback)
					: new SitemapListBean(getSitemapBeans(uriInfo.getAbsolutePathBuilder().build()));
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/{sitemapname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response getSitemapData(@Context HttpHeaders headers, @PathParam("sitemapname") String sitemapname,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", new String[] { uriInfo.getPath(), type });
		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getSitemap(sitemapname, uriInfo.getBaseUriBuilder().build()), callback) : getSitemap(sitemapname,
					uriInfo.getBaseUriBuilder().build());
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@PUT
	@Path("/{sitemapname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response putSitemapData(@Context HttpHeaders headers, @PathParam("sitemapname") String sitemapname,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			WidgetConfigBean sitemap) {
		logger.debug("Received HTTP PUT request at '{}' for media type '{}'.", new String[] { uriInfo.getPath(), type });
		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					saveSitemap(sitemapname, uriInfo.getBaseUriBuilder().build(), sitemap), callback) : saveSitemap(
					sitemapname, uriInfo.getBaseUriBuilder().build(), sitemap);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@POST
	@Path("/{sitemapname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response postSitemapData(@Context HttpHeaders headers, @PathParam("sitemapname") String sitemapname,
			@QueryParam("type") String type, @FormParam("copy") String copyName,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP POST request at '{}' for media type '{}'.",
				new String[] { uriInfo.getPath(), type });
		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new SitemapListBean(createSitemap(sitemapname, copyName, uriInfo.getAbsolutePathBuilder().build())),
					callback)
					: new SitemapListBean(
							createSitemap(sitemapname, copyName, uriInfo.getAbsolutePathBuilder().build()));
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@DELETE
	@Path("/{sitemapname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response deleteSitemapData(@Context HttpHeaders headers, @PathParam("sitemapname") String sitemapname,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP DELETE request at '{}' for media type '{}'.", new String[] { uriInfo.getPath(),
				type });
		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new SitemapListBean(deleteSitemap(sitemapname, uriInfo.getAbsolutePathBuilder().build())), callback)
					: new SitemapListBean(deleteSitemap(sitemapname, uriInfo.getAbsolutePathBuilder().build()));
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	public Collection<SitemapBean> getSitemapBeans(URI uri) {
		Collection<SitemapBean> beans = new LinkedList<SitemapBean>();
		logger.debug("Received HTTP GET request at '{}'.", UriBuilder.fromUri(uri).build().toASCIIString());
		ModelRepository modelRepository = RESTApplication.getModelRepository();
		for (String modelName : modelRepository.getAllModelNamesOfType("sitemap")) {
			Sitemap sitemap = (Sitemap) modelRepository.getModel(modelName);
			if (sitemap != null) {
				SitemapBean bean = new SitemapBean();
				bean.name = StringUtils.removeEnd(modelName, SITEMAP_FILEEXT);
				bean.link = UriBuilder.fromUri(uri).path(bean.name).build().toASCIIString();
				bean.homepage = new PageBean();
				bean.homepage.link = bean.link + "/" + sitemap.getName();
				beans.add(bean);
			}
		}
		return beans;
	}

	public Collection<SitemapBean> deleteSitemap(String sitemapname, URI uri) {
		String fname = new String("configurations/sitemaps/" + sitemapname + SITEMAP_FILEEXT);

		// Delete the sitemap
		File file = new File(fname);
		file.delete();

		// Update the model repository
		ModelRepository repo = RESTApplication.getModelRepository();
		repo.removeModel(sitemapname + SITEMAP_FILEEXT);

		// Now return the sitemap list
		return getSitemapBeans(uri);
	}

	public Collection<SitemapBean> createSitemap(String sitemapname, String copyname, URI uri) {
		String fname = new String("configurations/sitemaps/" + sitemapname + SITEMAP_FILEEXT);

		try {
			List<String> sitemapData;
			if (copyname != null && !copyname.isEmpty()) {
				String fcopyname = new String("configurations/sitemaps/" + copyname + SITEMAP_FILEEXT);

				sitemapData = IOUtils.readLines(new FileInputStream(fcopyname));

				// Now find the sitemap name and replace it!
				for (int cnt = 0; cnt < sitemapData.size(); cnt++) {
					Matcher matcher = SITEMAP_DEFINITION.matcher(sitemapData.get(cnt));

					if (matcher.matches()) {
						sitemapData.set(cnt, "sitemap " + sitemapname + " label=\"" + matcher.group(2) + "\"");
						break;
					}
				}
			} else {
				// Default to a new file
				sitemapData = new ArrayList<String>();
				sitemapData.add("sitemap " + sitemapname + " label=\"Main Menu\"");
				sitemapData.add("{");
				sitemapData.add("}");
			}

			// Check if the file exists
			File file = new File(fname);
			if (!file.exists()) {
				// Create the new sitemap
				FileWriter fw;
				fw = new FileWriter(file, false);
				BufferedWriter out = new BufferedWriter(fw);

				IOUtils.writeLines(sitemapData, "\r\n", out);

				out.close();

				// Update the model repository
				ModelRepository repo = RESTApplication.getModelRepository();
				if (repo != null) {
					InputStream inFile;
					try {
						inFile = new FileInputStream(fname);
						repo.addOrRefreshModel(sitemapname + SITEMAP_FILEEXT, inFile);
					} catch (FileNotFoundException e) {
						logger.debug("Error refreshing new sitemap " + sitemapname + ":", e);
					}
				}
			}
		} catch (IOException e) {
			logger.debug("Error writing to sitemap file " + sitemapname + ":", e);
		}

		// Now return the sitemap list
		return getSitemapBeans(uri);
	}

	private void writeWidget(BufferedWriter out, java.util.List<WidgetConfigBean> widgets, int level) {
		String indent = new String();
		for (int c = 0; c < level; c++)
			indent += "\t";

		for (WidgetConfigBean child : widgets) {
			try {
				out.write(indent + child.type + " ");
				if (child.item != null && !child.item.isEmpty())
					out.write("item=" + child.item + " ");
				if (child.label != null && !child.label.isEmpty())
					out.write("label=\"" + child.label + "\" ");
				if (child.icon != null && !child.icon.isEmpty())
					out.write("icon=\"" + child.icon + "\" ");

				if (child.type.equals("Setpoint")) {
					if (child.minValue != null)
						out.write("minValue=" + child.minValue + " ");
					if (child.maxValue != null)
						out.write("maxValue=" + child.maxValue + " ");
					if (child.step != null)
						out.write("step=" + child.step + " ");
				}

				if (child.type.equals("Image")) {
					if (child.url != null)
						out.write("url=" + child.url + " ");
				}

				if (child.type.equals("Switch")) {
					if (child.mappings != null && !child.mappings.isEmpty())
						out.write("mappings=\"" + child.mappings + "\" ");
				}

				if (child.type.equals("Selection")) {
					if (child.mappings != null && !child.mappings.equals("") & !child.mappings.equals("[]"))
						out.write("mappings=\"" + child.mappings + "\" ");
				}

				if (child.type.equals("Group") | child.type.equals("Frame")) {
					out.write("{\r\n");
					writeWidget(out, child.widgets, level + 1);
					out.write(indent + "}");
				}
				out.write(indent + "\r\n");
			} catch (IOException e) {
				logger.debug("Error writing sitemap :", e);
			}
		}
	}

	public WidgetConfigBean saveSitemap(String sitemapname, URI uri, WidgetConfigBean sitemap) {
		String fname = new String("configurations/sitemaps/" + sitemapname + SITEMAP_FILEEXT);

		if (sitemap == null)
			return null;

		if (sitemap.widgets == null)
			return null;

		// Check if the file exists
		File file = new File(fname);
		// Create the new sitemap
		FileWriter fw;
		try {
			fw = new FileWriter(file, false);
			BufferedWriter out = new BufferedWriter(fw);

			WidgetConfigBean main = sitemap.widgets.get(0);

			out.write("sitemap " + sitemapname + " label=\"" + main.label + "\"\r\n{\r\n");

			writeWidget(out, main.widgets, 1);

			out.write("}\r\n");
			out.close();
		} catch (IOException e) {
			logger.debug("Error writing to sitemap file " + sitemapname + ":", e);
		}

		// Update the model repository
		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo != null) {
			InputStream inFile;
			try {
				inFile = new FileInputStream(fname);
				repo.addOrRefreshModel(sitemapname + SITEMAP_FILEEXT, inFile);
			} catch (FileNotFoundException e) {
				logger.debug("Error refreeshing new sitemap " + sitemapname + ":", e);
			}
		}

		// Now return the sitemap
		return getSitemap(sitemapname, uri);
	}

	static private WidgetConfigBean getSitemap(String sitemapName, URI uri) {
		WidgetConfigBean bean = new WidgetConfigBean();
		Sitemap sitemap = getSitemap(sitemapName);
		bean.label = sitemap.getLabel();
		bean.icon = sitemap.getIcon();
		if (sitemap.getChildren() != null) {
			for (Widget widget : sitemap.getChildren()) {
				WidgetConfigBean subWidget = createWidgetBean(sitemapName, widget, uri);
				bean.widgets.add(subWidget);
			}
		}
		return bean;
	}

	static private WidgetConfigBean createWidgetBean(String sitemapName, Widget widget, URI uri) {
		ItemUIRegistry itemUIRegistry = RESTApplication.getItemUIRegistry();
		WidgetConfigBean bean = new WidgetConfigBean();
		if (widget.getItem() != null) {
			Item item = ItemResource.getItem(widget.getItem());
			if (item != null) {
				bean.item = item.getName();
			}
		}
		bean.icon = widget.getIcon();
		bean.label = widget.getLabel();
		bean.type = widget.eClass().getName();
		if (widget instanceof LinkableWidget) {
			LinkableWidget linkableWidget = (LinkableWidget) widget;
			EList<Widget> children = itemUIRegistry.getChildren(linkableWidget);
			for (Widget child : children) {
				bean.widgets.add(createWidgetBean(sitemapName, child, uri));// ,
																			// widgetId));
			}

		}
		if (widget instanceof Switch) {
			Switch switchWidget = (Switch) widget;
			for (Mapping mapping : switchWidget.getMappings()) {
				MappingBean mappingBean = new MappingBean();
				mappingBean.command = mapping.getCmd();
				mappingBean.label = mapping.getLabel();
				bean.mappings.add(mappingBean);
			}
		}
		if (widget instanceof Selection) {
			Selection selectionWidget = (Selection) widget;
			for (Mapping mapping : selectionWidget.getMappings()) {
				MappingBean mappingBean = new MappingBean();
				mappingBean.command = mapping.getCmd();
				mappingBean.label = mapping.getLabel();
				bean.mappings.add(mappingBean);
			}
		}
		if (widget instanceof Slider) {
			Slider sliderWidget = (Slider) widget;
			bean.sendFrequency = sliderWidget.getFrequency();
			bean.switchSupport = sliderWidget.isSwitchEnabled();
		}
		if (widget instanceof List) {
			org.openhab.model.sitemap.List listWidget = (org.openhab.model.sitemap.List) widget;
			bean.separator = listWidget.getSeparator();
		}
		if (widget instanceof Image) {
			Image imageWidget = (Image) widget;
			String wId = itemUIRegistry.getWidgetId(widget);
			bean.url = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/proxy?sitemap=" + sitemapName
					+ ".sitemap&widgetId=" + wId;
			if (imageWidget.getRefresh() > 0) {
				bean.refresh = imageWidget.getRefresh();
			}
		}
		if (widget instanceof Video) {
			String wId = itemUIRegistry.getWidgetId(widget);
			bean.url = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/proxy?sitemap=" + sitemapName
					+ ".sitemap&widgetId=" + wId;
		}
		if (widget instanceof Webview) {
			Webview webViewWidget = (Webview) widget;
			bean.url = webViewWidget.getUrl();
			bean.height = webViewWidget.getHeight();
		}
		if (widget instanceof Chart) {
			Chart chartWidget = (Chart) widget;
			bean.service = chartWidget.getService();
			bean.period = chartWidget.getPeriod();
			if (chartWidget.getRefresh() > 0) {
				bean.refresh = chartWidget.getRefresh();
			}
		}
		if (widget instanceof Setpoint) {
			Setpoint setpointWidget = (Setpoint) widget;
			bean.minValue = setpointWidget.getMinValue();
			bean.maxValue = setpointWidget.getMaxValue();
			bean.step = setpointWidget.getStep();
		}
		return bean;
	}

	static public Sitemap getSitemap(String sitemapname) {
		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo != null) {
			Sitemap sitemap = (Sitemap) repo.getModel(sitemapname + SITEMAP_FILEEXT);
			return sitemap;
		}
		return null;
	}
}
