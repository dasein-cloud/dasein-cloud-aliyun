/**
 * Copyright (C) 2009-2015 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.aliyun.platform.model;

public class DatabaseProduct {

    private String  name;
    private double  memory;
    private int     maxConnection;
    private int     maxIops;
    private int     minStorage;
    private String  license;
    private String  currency;
    private float 	hourlyPrice;
    private int     storageInGigabytes;

    public String getLicense() {
        return license;
    }

    public void setLicense( String license ) {
        this.license = license;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency( String currency ) {
        this.currency = currency;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double memory) {
        this.memory = memory;
    }

    public int getMaxConnection() {
        return maxConnection;
    }

    public void setMaxConnection(int maxConnection) {
        this.maxConnection = maxConnection;
    }

    public int getMaxIops() {
        return maxIops;
    }

    public void setMaxIops(int maxIops) {
        this.maxIops = maxIops;
    }

    public int getMinStorage() {
        return minStorage;
    }

    public void setMinStorage( int minStorage ) {
        this.minStorage = minStorage;
    }

	public float getHourlyPrice() {
		return hourlyPrice;
	}

	public void setHourlyPrice(float hourlyPrice) {
		this.hourlyPrice = hourlyPrice;
	}

	public int getStorageInGigabytes() {
		return storageInGigabytes;
	}

	public void setStorageInGigabytes(int storageInGigabytes) {
		this.storageInGigabytes = storageInGigabytes;
	}
    
}
