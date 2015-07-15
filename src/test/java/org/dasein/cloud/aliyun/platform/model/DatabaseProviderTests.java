/**
 * Copyright (C) 2009-2015 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License\tVersion 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing\tsoftware
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND\teither express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.dasein.cloud.aliyun.platform.model;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dasein.cloud.InternalException;
import org.junit.Test;
import org.junit.Assert.*;

/**
 * Tests for DatabaseProvider
 * @author Jane Wang
 * @since 2015.05.01
 */
public class DatabaseProviderTests {

	@Test
	public void testFromFile() {
		try {
			
			DatabaseProvider provider = DatabaseProvider.fromFile("/platform/dbproducts.json", "Aliyun");
			assertNotNull(provider);
			
			DatabaseEngine engine = provider.findEngine("MySQL");
			assertNotNull(engine);
			
			for (DatabaseRegion region: engine.getRegions()) {
				for (String name : region.getName()) {
					System.out.print(name + " ");
				}
				System.out.println();
				for (DatabaseProduct product: region.getProducts()) {
					System.out.println("      " + product.getName() + ": " + product.getCurrency() + "\t" + product.getHourlyPrice() + 
							"\t" + product.getLicense() + "\t" + product.getMaxConnection() + "\t" + product.getMaxIops() + 
							"\t" + product.getMemory() + "\t" + product.getMinStorage());
				}
			}
			
		} catch (InternalException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testBasic() {
		List<String> accessList = new ArrayList<String>();
		accessList.add("172.168.2.0/8");
		accessList.add("www.sinal.com");
		accessList.add("168.192.3.4");
		StringBuilder accessBuilder = new StringBuilder();
		Iterator<String> access = accessList.iterator();
		while (access.hasNext()) {
			String cidr = access.next();
			if (!cidr.equals("168.192.3.4")) {
				accessBuilder.append(cidr + ",");
			}
		}
		System.out.println(accessBuilder.toString());
		accessBuilder.deleteCharAt(accessBuilder.length() - 1);
		System.out.println(accessBuilder.toString());
	}
	
	@Test
	public void testDatetime() throws ParseException {
		
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 6);
		SimpleDateFormat format = new SimpleDateFormat("HH:00'Z'");
		assertEquals(format.format(cal.getTime()), "06:00Z");
		cal.set(Calendar.HOUR_OF_DAY, 23);
		assertEquals(format.format(cal.getTime()), "23:00Z");
		
		format = new SimpleDateFormat("EEEE");
		cal.set(Calendar.DAY_OF_WEEK, 2); //start from Sunday
		assertEquals(format.format(cal.getTime()), "Monday");
		
		SimpleDateFormat formatter = new SimpleDateFormat("HH:mm'Z'");
		String timeStr = "06:32Z";
		cal.setTime(formatter.parse(timeStr));
		assertEquals(cal.get(Calendar.HOUR_OF_DAY), 6);
		assertEquals(cal.get(Calendar.MINUTE), 32);
	}
	
	@Test
	public void testDBName() {
		Pattern pattern = Pattern.compile("^[a-z]{1}[a-z0-9_]{1,64}$");
		Matcher matcher = pattern.matcher("aaa1292_jiwej");
		assertTrue(matcher.find());
		matcher = pattern.matcher("1292a_jiwej");
		assertFalse(matcher.find());
	}
	
}
