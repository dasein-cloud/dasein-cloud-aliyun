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
package org.dasein.cloud.aliyun.platform;

import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.platform.AbstractPlatformServices;
import org.dasein.cloud.platform.MQSupport;
import org.dasein.cloud.platform.RelationalDatabaseSupport;

/**
 * Created by Jane Wang on 7/10/2015.
 * 
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunPlatform extends AbstractPlatformServices<Aliyun> {

	public AliyunPlatform(Aliyun provider) {
		super(provider);
	}

	@Override
	public MQSupport getMessageQueueSupport() {
		return new AliyunMessageQueue(getProvider());
	}

	@Override
	public RelationalDatabaseSupport getRelationalDatabaseSupport() {
		return new AliyunRelationalDatabase(getProvider());
	}
}
