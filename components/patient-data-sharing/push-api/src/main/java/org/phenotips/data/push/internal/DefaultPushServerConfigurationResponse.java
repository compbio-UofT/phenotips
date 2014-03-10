/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.phenotips.data.push.internal;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.phenotips.data.shareprotocol.ShareProtocol;
import org.phenotips.data.push.PushServerConfigurationResponse;
import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.configuration.RecordConfiguration;
import org.phenotips.configuration.RecordConfigurationManager;

public class DefaultPushServerConfigurationResponse extends DefaultPushServerResponse implements PushServerConfigurationResponse
{
    DefaultPushServerConfigurationResponse(JSONObject serverResponse)
    {
        super(serverResponse);
    }

    protected Set<String> getSetFromJSONList(String key)
    {
        JSONArray stringList = response.optJSONArray(key);
        if (stringList == null)
            return null;

        Set<String> result = new TreeSet<String>();  // to make sure order is unchanged
        for(Object field : stringList) {
            result.add(field.toString());
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<String> getRemoteUserGroups()
    {
        return getSetFromJSONList(ShareProtocol.SERVER_JSON_GETINFO_KEY_NAME_USERGROUPS);
    }

    @Override
    public Set<String> getRemoteAcceptedPatientFields()
    {
        return getRemoteAcceptedPatientFields(null);
    }

    @Override
    public Set<String> getRemoteAcceptedPatientFields(String groupName)
    {
        return getSetFromJSONList(ShareProtocol.SERVER_JSON_GETINFO_KEY_NAME_ACCEPTEDFIELDS);
    }

    @Override
    public Set<String> getPushableFields()
    {
        return getPushableFields(null);
    }

    @Override
    public Set<String> getPushableFields(String groupName)
    {
        try
        {
            Set<String> remoteAcceptedFields = getRemoteAcceptedPatientFields(groupName);

            if (remoteAcceptedFields == null)
                return Collections.emptySet();

            RecordConfigurationManager configurationManager = ComponentManagerRegistry.getContextComponentManager().
                                                              getInstance(RecordConfigurationManager.class);

            RecordConfiguration patientConfig = configurationManager.getActiveConfiguration();

            Set<String> commonFields = new TreeSet<String>(patientConfig.getEnabledNonIdentifiableFieldNames());

            commonFields.retainAll(remoteAcceptedFields);  // of the non-personal fields available,
                                                           // only keep those fields enable doin the remote server
            return commonFields;
        } catch(Exception ex) {
            return Collections.emptySet();
        }
    }

    @Override
    public String getRemoteUserToken()
    {
        return valueOrNull(ShareProtocol.SERVER_JSON_GETINFO_KEY_NAME_USERTOKEN);
    }
}
