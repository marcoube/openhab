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

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

import org.atmosphere.annotation.Suspend.SCOPE;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.jersey.SuspendResponse;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.io.rest.internal.RESTApplication;
import org.openhab.io.rest.internal.broadcaster.GeneralBroadcaster;
import org.openhab.io.rest.internal.resources.beans.BundleListBean;
import org.openhab.io.rest.internal.resources.beans.BundleBean;
import org.openhab.ui.items.ItemUIRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
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
@Path(BundleResource.PATH_BUNDLE)
public class BundleResource {

	private static final Logger	logger		= LoggerFactory.getLogger(BundleResource.class);

	/** The URI path to this resource */
	public static final String	PATH_BUNDLE = "bundle";


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
	    			new JSONWithPadding(new BundleListBean(getBundles(uriInfo.getPath())), callback) : new BundleListBean(getBundles(uriInfo.getPath()));
	    	return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
    }
	
/*
	@GET
	@Path("/{bundlename: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public SuspendResponse<Response> getItemData(@Context HttpHeaders headers, @PathParam("bundlename") String bundlename,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback,
			@HeaderParam(HeaderConfig.X_ATMOSPHERE_TRANSPORT) String atmosphereTransport,
			@Context AtmosphereResource resource) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type );

		if (atmosphereTransport == null || atmosphereTransport.isEmpty()) {
			final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
			if (responseType != null) {
				final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
						getBundleBean(bundlename, true), callback) : getBundleBean(bundlename, true);
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
*/
	public static BundleBean createBundleBean(Bundle bundle, String uriPath, boolean detail) {
		BundleBean bean = new BundleBean();

		bean.name = bundle.getSymbolicName();
		bean.version = bundle.getVersion().toString();
		bean.modified = bundle.getLastModified();
		bean.id = bundle.getBundleId();
		bean.state = bundle.getState();
		bean.link = uriPath;

		return bean;
	}

	static public Item getBundle(String itemname, String uriPath) {
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
/*
	private ItemBean getBundleBean(String bundlename, String uriPath) {

		Item item = getItem(itemname);
		if (item != null) {
			return createBundleBean(item, uriInfo.getBaseUri().toASCIIString(), true);
		} else {
			logger.info("Received HTTP GET request at '{}' for the unknown item '{}'.", uriInfo.getPath(), itemname);
			throw new WebApplicationException(404);
		}
	}
*/
	private List<BundleBean> getBundles(String uriPath) {
		List<BundleBean> beans = new LinkedList<BundleBean>();

		BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass())
                .getBundleContext();

		for (Bundle bundle : bundleContext.getBundles()) {
			logger.info(bundle.toString());
			BundleBean bean = (BundleBean)createBundleBean(bundle, uriPath, false);

			if(bean != null)
				beans.add(bean);
		}
		return beans;
	}

}
