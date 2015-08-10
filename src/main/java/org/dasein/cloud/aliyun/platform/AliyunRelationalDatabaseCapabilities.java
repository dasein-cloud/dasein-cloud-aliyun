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

import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.aliyun.Aliyun;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.util.NamingConstraints;

/**
 * Created by Jane Wang on 7/10/2015.
 * 
 * @author Jane Wang
 * @since 2015.05.01
 */
public class AliyunRelationalDatabaseCapabilities extends
		AbstractCapabilities<Aliyun> implements RelationalDatabaseCapabilities {

	public AliyunRelationalDatabaseCapabilities( @Nonnull Aliyun provider) {
		super(provider);
	}
	
	@Override
	public String getProviderTermForBackup(Locale locale) {
		return "Backup";
	}

	@Override
	public String getProviderTermForDatabase(Locale locale) {
		return "RDS";
	}

	@Override
	public String getProviderTermForSnapshot(Locale locale) {
		return null;
	}

	@Override
	public boolean supportsFirewallRules() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsHighAvailability() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsLowAvailability() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsMaintenanceWindows() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsAlterDatabase() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsSnapshots() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsDatabaseBackups() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsScheduledDatabaseBackups() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsDemandBackups() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsRestoreBackup() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsDeleteBackup() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsBackupConfigurations() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public NamingConstraints getRelationalDatabaseNamingConstraints()
			throws CloudException, InternalException {
		return NamingConstraints.getAlphaNumeric(1, 64).withRegularExpression(
                "^[a-z]{1}[a-z0-9_]{0,63}$").withNoSpaces();
	}

}
