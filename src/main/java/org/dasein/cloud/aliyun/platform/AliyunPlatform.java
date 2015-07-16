package org.dasein.cloud.aliyun.platform;

import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.platform.AbstractPlatformServices;
import org.dasein.cloud.platform.CDNSupport;
import org.dasein.cloud.platform.KeyValueDatabaseSupport;
import org.dasein.cloud.platform.MQSupport;
import org.dasein.cloud.platform.MonitoringSupport;
import org.dasein.cloud.platform.PushNotificationSupport;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.platform.bigdata.DataWarehouseSupport;

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
