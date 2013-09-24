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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.openhab.core.items.Item;
import org.openhab.io.rest.internal.RESTApplication;
import org.openhab.io.rest.internal.resources.beans.ItemConfigBean;
import org.openhab.io.rest.internal.resources.beans.ItemConfigListBean;
import org.openhab.io.rest.internal.resources.beans.ItemPersistenceBean;
import org.openhab.model.core.ModelRepository;
import org.openhab.model.items.ItemModel;
import org.openhab.model.items.ModelBinding;
import org.openhab.model.items.ModelItem;
import org.openhab.model.persistence.persistence.PersistenceConfiguration;
import org.openhab.model.persistence.persistence.PersistenceModel;
import org.openhab.model.persistence.persistence.Strategy;
import org.openhab.model.persistence.persistence.impl.GroupConfigImpl;
import org.openhab.model.persistence.persistence.impl.ItemConfigImpl;
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
@Path(ItemConfigResource.PATH_CONFIG)
public class ItemConfigResource {

	private static final Logger logger = LoggerFactory
			.getLogger(ItemConfigResource.class);

	private static final Pattern LABEL_PATTERN = Pattern
			.compile("(.*?)\\[(.*)\\]");
	private static final Pattern FORMAT_PATTERN = Pattern
			.compile("(?i)MAP\\((.*?)\\):(.*)");
	private static final Pattern UNIT_PATTERN = Pattern
			.compile("(%\\.?\\d?[dfsu])(.*)");

	/** The URI path to this resource */
	public static final String PATH_CONFIG = "config/items";

	@Context
	UriInfo uriInfo;

	@GET
	@Produces({ MediaType.WILDCARD })
	public Response getItems(
			@Context HttpHeaders headers,
			@QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.",
				uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(
				headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType
					.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new ItemConfigListBean(getItemConfigBeanList()), callback)
					: new ItemConfigListBean(getItemConfigBeanList());
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response getItem(
			@Context HttpHeaders headers,
			@QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.",
				uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(
				headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType
					.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getItemConfigBean(itemname), callback)
					: getItemConfigBean(itemname);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@PUT
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response putItem(
			@Context HttpHeaders headers,
			@QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			ItemConfigBean item) {
		logger.debug("Received HTTP PUT request at '{}' for media type '{}'.",
				uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(
				headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType
					.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					putItemConfigBean(itemname, item), callback)
					: putItemConfigBean(itemname, item);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@POST
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response postItem(
			@Context HttpHeaders headers,
			@QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			ItemConfigBean item) {
		logger.debug("Received HTTP POST request at '{}' for media type '{}'.",
				uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(
				headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType
					.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					postItemConfigBean(itemname, item), callback)
					: postItemConfigBean(itemname, item);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@DELETE
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response deleteItem(
			@Context HttpHeaders headers,
			@QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			ItemConfigBean item) {
		logger.debug(
				"Received HTTP DELETE request at '{}' for media type '{}'.",
				uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(
				headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType
					.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new ItemConfigListBean(deleteItemConfigBean()), callback)
					: new ItemConfigListBean(deleteItemConfigBean());
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	private List<ItemConfigBean> readItemModel(ItemModel items, String model) {
		List<ItemConfigBean> beanList = new ArrayList<ItemConfigBean>();

		EList<ModelItem> modelList = items.getItems();
		for (ModelItem item : modelList) {
			ItemConfigBean bean = new ItemConfigBean();
			bean.model = model;

			if (item.getLabel() != null) {
				Matcher labelMatcher = LABEL_PATTERN.matcher(item.getLabel());

				if (labelMatcher.matches()) {
					bean.label = labelMatcher.group(1).trim();
					bean.format = labelMatcher.group(2).trim();
				} else
					bean.label = item.getLabel();

				if (bean.format != null && bean.format.length() > 0) {
					// unescape the format string
					String format = bean.format.trim();
					format = StringEscapeUtils.unescapeJava(format);

					int posFormatEnd = 0;
					int posUnitStart = 0;
					int cnt = 0;
					for (char ch : format.toCharArray()) {
						if(posFormatEnd == 0) {
							if(ch == ' ')
								posFormatEnd = cnt;
						}
						if(posUnitStart == 0) {
							if(ch == '%')
								posFormatEnd = 0;
							else if (ch != ' ')
								posUnitStart = cnt;
						}
							
						cnt++;
					}

					bean.units = format.substring(posUnitStart);
					bean.units.trim();

					bean.format = format.substring(0, posFormatEnd);
					bean.format.trim();
				}
			}

			bean.icon = item.getIcon();
			bean.name = item.getName();
			if (item.getType() == null)
				bean.type = "GroupItem";
			else
				bean.type = item.getType() + "Item";

			bean.bindings = new ArrayList<String>();
			EList<ModelBinding> bindingList = item.getBindings();
			for (ModelBinding binding : bindingList) {
				bean.bindings.add(binding.getConfiguration());
				bean.binding = binding.getType();
			}

			bean.groups = new ArrayList<String>();
			EList<String> groupList = item.getGroups();
			for (String group : groupList) {
				bean.groups.add(group.toString());
			}

			ModelRepository repo = RESTApplication.getModelRepository();
			if (repo == null)
				return null;

			File folder = new File("configurations/persistence/");
			File[] listOfFiles = folder.listFiles();

			if (listOfFiles == null)
				return null;

			bean.persistence = new ArrayList<ItemPersistenceBean>();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile()
						& listOfFiles[i].getName().endsWith(".persist")) {
					ItemPersistenceBean p = getItemPersistence(item,
							listOfFiles[i].getName());
					if (p != null)
						bean.persistence.add(p);
				}
			}

			beanList.add(bean);
		}

		return beanList;
	}

	private List<ItemConfigBean> getItemConfigBeanList() {
		List<ItemConfigBean> beanList = new ArrayList<ItemConfigBean>();

		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo == null)
			return null;

		File folder = new File("configurations/items/");
		File[] listOfFiles = folder.listFiles();

		if (listOfFiles == null)
			return null;

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()
					& listOfFiles[i].getName().endsWith(".items")) {
				ItemModel items = (ItemModel) repo.getModel(listOfFiles[i]
						.getName());
				List<ItemConfigBean> beans = readItemModel(
						items,
						listOfFiles[i].getName().substring(0,
								listOfFiles[i].getName().indexOf('.')));
				if (beans != null)
					beanList.addAll(beans);
			}
		}

		return beanList;
	}

	private ItemConfigBean getItemConfigBean(String itemname) {
		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo == null)
			return null;

		File folder = new File("configurations/items/");
		File[] listOfFiles = folder.listFiles();

		if (listOfFiles == null)
			return null;

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()
					& listOfFiles[i].getName().endsWith(".items")) {
				ItemModel items = (ItemModel) repo.getModel(listOfFiles[i]
						.getName());
				List<ItemConfigBean> beans = readItemModel(
						items,
						listOfFiles[i].getName().substring(0,
								listOfFiles[i].getName().indexOf('.')));

				for (ItemConfigBean bean : beans) {
					if (bean.name.equals(itemname))
						return bean;
				}
			}
		}

		return null;
	}

	// Save an item
	private ItemConfigBean putItemConfigBean(String itemname,
			ItemConfigBean item) {

		// TODO: escape the string using unescapeJava(String str)

		return getItemConfigBean(itemname);
	}

	// Create a new item
	private ItemConfigBean postItemConfigBean(String itemname,
			ItemConfigBean item) {

		return getItemConfigBean(itemname);
	}

	// Delete an item
	private List<ItemConfigBean> deleteItemConfigBean() {

		return getItemConfigBeanList();
	}

	private static ItemPersistenceBean getItemPersistence(ModelItem item,
			String service) {
		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo == null)
			return null;

		PersistenceModel models = (PersistenceModel) repo.getModel(service);
		if (models == null)
			return null;

		EList<PersistenceConfiguration> configList = models.getConfigs();
		for (PersistenceConfiguration config : configList) {
			for (int cnt = 0; cnt < config.getItems().size(); cnt++) {
				EObject modelItem = config.getItems().get(cnt);
				if (modelItem instanceof GroupConfigImpl) {
					for (String group : item.getGroups()) {
						if (((GroupConfigImpl) modelItem).getGroup()
								.equalsIgnoreCase(group)) {
							ItemPersistenceBean bean = new ItemPersistenceBean();
							bean.service = service.substring(0,
									service.indexOf('.'));
							bean.group = ((GroupConfigImpl) modelItem)
									.getGroup();
							bean.strategies = new ArrayList<String>();

							for (int str = 0; str < config.getStrategies()
									.size(); str++) {
								Strategy strategyItem = config.getStrategies()
										.get(str);
								bean.strategies.add(strategyItem.getName());
							}
							return bean;
						}
					}
				}
				if (modelItem instanceof ItemConfigImpl) {
					if (((ItemConfigImpl) modelItem).getItem().equals(item)) {
						ItemPersistenceBean bean = new ItemPersistenceBean();
						bean.service = service.substring(0,
								service.indexOf('.'));
						bean.item = ((ItemConfigImpl) modelItem).getItem();
						bean.strategies = new ArrayList<String>();

						for (int str = 0; str < config.getStrategies().size(); str++) {
							Strategy strategyItem = config.getStrategies().get(
									str);
							bean.strategies.add(strategyItem.getName());
						}
						return bean;
					}
				}
			}
		}

		return null;
	}
}
