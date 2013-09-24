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
package org.openhab.io.rest.internal.resources.beans;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.annotate.JsonSerialize;


/**
 * This is a java bean that is used with JAXB to serialize items
 * to XML or JSON.
 *  
 * @author Chris Jackson
 * @since 1.3.0
 *
 */
@XmlRootElement(name="history")
public class ItemHistoryBean {

	public String type;
	public String name;	
	public String link;

	public String icon;
	public String label;
	public String units;
	public String format;
	public String map;
	
	public List<String> groups;
	
	public List<ItemPersistenceBean> persistence;	
	
	public String timestart;
	public String timeend;
	public String statemax;
	public String timemax;
	public String statemin;
	public String timemin;
	public String stateavg;
	public String datapoints;

	public List<HistoryDataBean> data;
	
	public ItemHistoryBean() {};

	public void addData(String time, String value) {
		if(data == null)
			data = new ArrayList<HistoryDataBean>();
		HistoryDataBean newVal = new HistoryDataBean();
		newVal.time = time;
		newVal.value = value;
		data.add(newVal);
	}
	
	@JsonSerialize(using = ItemHistoryBean.JsonHistorySerializer.class)
	public static class HistoryDataBean {
//		@XmlAttribute
		public String time;
		
//		@XmlValue
		public String value;
	}

	public class JsonHistorySerializer extends JsonSerializer<HistoryDataBean>{

		@Override
		public void serialize(HistoryDataBean history, JsonGenerator gen, SerializerProvider provider) throws IOException,
				JsonProcessingException {
			String jsonHistory = new String("["+history.time+","+history.value+"]");			
	        gen.writeString(jsonHistory);
		}
	 
	}
}
