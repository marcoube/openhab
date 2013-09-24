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
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;

import org.atmosphere.annotation.Suspend.SCOPE;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.jersey.SuspendResponse;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistenceService;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.openhab.core.persistence.FilterCriteria.Ordering;
import org.openhab.core.persistence.extensions.PersistenceExtensions;
import org.openhab.core.types.State;
import org.openhab.io.rest.internal.RESTApplication;
import org.openhab.io.rest.internal.broadcaster.GeneralBroadcaster;
import org.openhab.io.rest.internal.resources.beans.ItemBean;
import org.openhab.io.rest.internal.resources.beans.ItemConfigBean;
import org.openhab.io.rest.internal.resources.beans.ItemHistoryBean;
import org.openhab.io.rest.internal.resources.beans.ItemHistoryListBean;
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
import org.openhab.model.persistence.scoping.GlobalStrategies;
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
@Path(HistoryResource.PATH_HISTORY)
public class HistoryResource {

	private static final Logger	logger		= LoggerFactory.getLogger(HistoryResource.class);

	private static final Pattern LABEL_PATTERN = Pattern.compile("(.*?)\\[(.*)\\]");

	/** The URI path to this resource */
	public static final String	PATH_HISTORY = "history";


	@Context UriInfo uriInfo;

	@GET
    @Produces( { MediaType.WILDCARD })
    public Response getItems(
    		@Context HttpHeaders headers,
    		@QueryParam("type") String type, 
    		@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type );

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if(responseType!=null) {
	    	Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ?
	    			new JSONWithPadding(new ItemHistoryListBean(getHistory()), callback) : new ItemHistoryListBean(getHistory());
	    	return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
    }
	
	
	@GET
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public SuspendResponse<Response> getItemData(@Context HttpHeaders headers, @PathParam("itemname") String itemname,
			@QueryParam("period") long period, @QueryParam("starttime") long start, @QueryParam("endtime") long end,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			@HeaderParam(HeaderConfig.X_ATMOSPHERE_TRANSPORT) String atmosphereTransport,
			@Context AtmosphereResource resource) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type );
		
		// Work out the start and end time given there's 3 possible parameters
		if(end == 0) {
			// End not supplied, so either start+period, or
			if(period > 0 & start > 0) {
				end = start + period;
			}
			else if(period > 0) {
				// Start and end not supplied. Provide data for the last "period".
				end = DateTime.now().getMillis();
				start = end - period;
			}
			else {
				// Nothing supplied! Just provide data for the last hour
				end = DateTime.now().getMillis();
				start = end - 3600000;
			}
		}
		else if(start == 0) {
			// Start not suppled, but end is!
			if(period > 0) {
				start = end - period;
			}
			else {
				// Only end time supplied! Just provide data for the last hour
				end = DateTime.now().getMillis();
				start = end - 3600000;
			}
		}

		if (atmosphereTransport == null || atmosphereTransport.isEmpty()) {
			final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
			if (responseType != null) {
				final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
						getItemHistoryBean(itemname, start, end), callback) : getItemHistoryBean(itemname, start, end);
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
	
	public static ItemHistoryBean createHistoryBean(Item item, boolean drillDown, String uriPath, long start, long end) {
		ItemHistoryBean bean = null;
		if (item instanceof GroupItem && drillDown) {
			//GroupItem groupItem = (GroupItem) item;
			//GroupItemBean groupBean = new GroupItemBean();
			//Collection<ItemBean> members = new HashSet<ItemBean>();
			//for(Item member : groupItem.getMembers()) {
			//members.add(createHistoryBean(member, false, uriPath, start, end));
			//}
			//groupBean.members = members.toArray(new
			//ItemHistoryBean[members.size()]);
			//bean = (ItemHistoryBean)groupBean;
			return null;
		} else {
			bean = new ItemHistoryBean();
		}

		bean.name = item.getName();

		bean.timestart = Long.toString(start);
		bean.timeend = Long.toString(end);

		DateTime timeBegin = new DateTime(start);
		DateTime timeEnd = new DateTime(end);

		logger.debug("History request {} from {} to {}", item.getName(), timeBegin, timeEnd);

		Iterable<HistoricItem> result = PersistenceExtensions.getAllStatesBetweenTimes(item, timeBegin, timeEnd);
		Iterator<HistoricItem> it = result.iterator();

		Long quantity = 0l;
		double average = 0;
		DecimalType minimum = null;
		DecimalType maximum = null;
		Date timeMinimum = null;
		Date timeMaximum = null;

		// Iterate through the data
		while (it.hasNext()) {
			HistoricItem historicItem = it.next();
			State state = historicItem.getState();
			if (state instanceof DecimalType) {
				DecimalType value = (DecimalType) state;

				bean.addData(Long.toString(historicItem.getTimestamp().getTime()), value.toString());

				average += value.doubleValue();
				quantity++;

				if (minimum == null || value.compareTo(minimum) < 0) {
					minimum = value;
					timeMinimum = historicItem.getTimestamp();
				}

				if (maximum == null || value.compareTo(maximum) > 0) {
					maximum = value;
					timeMaximum = historicItem.getTimestamp();
				}
			}
		}

		bean.datapoints = Long.toString(quantity);
		if(quantity > 0)
			bean.stateavg = Double.toString(average / quantity);

		if (minimum != null) {
			bean.statemin = minimum.toString();
			bean.timemin = Long.toString(timeMinimum.getTime());
		}

		if (maximum != null) {
			bean.statemax = maximum.toString();
			bean.timemax = Long.toString(timeMaximum.getTime());
		}

		bean.type = item.getClass().getSimpleName();
		bean.link = UriBuilder.fromUri(uriPath).path(HistoryResource.PATH_HISTORY).path(bean.name).build()
				.toASCIIString();

		return bean;
	}

	static public Item getItem(String itemname) {
		ItemUIRegistry registry = RESTApplication.getItemUIRegistry();
		if (registry != null) {
			try {
				Item item = registry.getItem(itemname);
				return item;
			} catch (ItemNotFoundException e) {
				logger.debug(e.getMessage());
			}
		}
		return null;
	}

	private ItemHistoryBean getItemHistoryBean(String itemname, long start, long end) {
		Item item = getItem(itemname);
		if (item != null) {
			return createHistoryBean(item, true, uriInfo.getBaseUri().toASCIIString(), start, end);
		} else {
			logger.info("Received HTTP GET request at '{}' for the unknown item '{}'.", uriInfo.getPath(), itemname);
			throw new WebApplicationException(404);
		}
	}

/*	private List<ItemHistoryBean> getHistory() {
		List<ItemHistoryBean> beans = new LinkedList<ItemHistoryBean>();
		ItemUIRegistry registry = RESTApplication.getItemUIRegistry();
		for(Item item : registry.getItems()) {
			ItemHistoryBean bean = (ItemHistoryBean)createHistoryBean(item, true, uriInfo.getBaseUri().toASCIIString(), DateTime.now().getMillis()-3600000, DateTime.now().getMillis());

			if(bean != null) {
				// Don't return data in the summary
				bean.data = null;

				beans.add(bean);
			}
		}
		return beans;
	}
	*/
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	private List<ItemHistoryBean> readItemModel(ItemModel items, String model) {
		List<ItemHistoryBean> beanList = new ArrayList<ItemHistoryBean>();

		EList<ModelItem> modelList = items.getItems();
		for (ModelItem item : modelList) {
			ItemHistoryBean bean = new ItemHistoryBean();

			if(item.getLabel() != null) {
				Matcher labelMatcher = LABEL_PATTERN.matcher(item.getLabel());

				if (labelMatcher.matches())
					bean.label  = labelMatcher.group(1).trim();
				else
					bean.label = item.getLabel();
			}

			bean.icon = item.getIcon();
			bean.name = item.getName();
			if(item.getType() == null)
				bean.type = "GroupItem";
			else
				bean.type = item.getType() + "Item";

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

			if(listOfFiles == null)
				return null;

			bean.persistence = new ArrayList<ItemPersistenceBean>();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile() & listOfFiles[i].getName().endsWith(".persist")) {
					ItemPersistenceBean p = getItemPersistence(item, listOfFiles[i].getName());
					if(p != null)
						bean.persistence.add(p);
			    }
			}

			// We're only interested in items with persistence enabled
			if(bean.persistence.size() > 0)
				beanList.add(bean);
		}

		return beanList;
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	private List<ItemHistoryBean> getHistory() {
		List<ItemHistoryBean> beanList = new ArrayList<ItemHistoryBean>();

		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo == null)
			return null;		

		File folder = new File("configurations/items/");
		File[] listOfFiles = folder.listFiles();
		
		if(listOfFiles == null)
			return null;

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile() & listOfFiles[i].getName().endsWith(".items")) {
				ItemModel items = (ItemModel) repo.getModel(listOfFiles[i].getName());
				List<ItemHistoryBean> beans = readItemModel(items, listOfFiles[i].getName().substring(0,listOfFiles[i].getName().indexOf('.')));
				if(beans != null)
					beanList.addAll(beans);
		    }
		}

		return beanList;
	}
	
	private static ItemPersistenceBean getItemPersistence(ModelItem item, String service) {
		ModelRepository repo = RESTApplication.getModelRepository();
		if (repo == null)
			return null;

		PersistenceModel models = (PersistenceModel) repo.getModel(service);
		if(models == null)
			return null;

		EList<PersistenceConfiguration> configList = models.getConfigs();
		for (PersistenceConfiguration config : configList) {
			for(int cnt = 0; cnt < config.getItems().size(); cnt++) {
				EObject modelItem = config.getItems().get(cnt);
				if(modelItem instanceof GroupConfigImpl) {
					for(String group : item.getGroups()) {
						if(((GroupConfigImpl) modelItem).getGroup().equalsIgnoreCase(group)) {
							ItemPersistenceBean bean = new ItemPersistenceBean();
							bean.service = service.substring(0, service.indexOf('.'));
							bean.group = ((GroupConfigImpl) modelItem).getGroup();
							bean.strategies = new ArrayList<String>(); 
	
							for(int str = 0; str < config.getStrategies().size(); str++) {
								Strategy strategyItem = config.getStrategies().get(str);
								bean.strategies.add(strategyItem.getName());
							}
							return bean;
						}
					}
				}
		 		if(modelItem instanceof ItemConfigImpl) {
					if(((ItemConfigImpl) modelItem).getItem().equals(item)) {
						ItemPersistenceBean bean = new ItemPersistenceBean();
						bean.service = service.substring(0, service.indexOf('.'));
						bean.item = ((ItemConfigImpl) modelItem).getItem();
						bean.strategies = new ArrayList<String>(); 

						for(int str = 0; str < config.getStrategies().size(); str++) {
							Strategy strategyItem = config.getStrategies().get(str);
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
