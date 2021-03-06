/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.data.rest.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.PatientWritePolicy;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.rest.Autolinker;
import org.phenotips.security.authorization.AuthorizationService;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.mockito.MockitoComponentMockingRule;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.xpn.xwiki.XWikiContext;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link DefaultPatientResourceImpl} component.
 */
public class DefaultPatientResourceImplTest
{
    private static final String UPDATE = "update";

    private static final String PATIENT_ID = "00000001";

    private static final String URI_STRING = "http://self/uri";

    private static final String EMPTY_JSON = "{}";

    @Rule
    public MockitoComponentMockingRule<PatientResource> mocker =
        new MockitoComponentMockingRule<>(DefaultPatientResourceImpl.class);

    @Mock
    private User currentUser;

    @Mock
    private Patient patient;

    @Mock
    private UriInfo uriInfo;

    private Logger logger;

    private PatientRepository repository;

    private AuthorizationService access;

    private DocumentReference patientDocument;

    private DefaultPatientResourceImpl patientResource;

    @Before
    public void setUp() throws ComponentLookupException, URISyntaxException
    {
        MockitoAnnotations.initMocks(this);

        Execution execution = mock(Execution.class);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        ComponentManager componentManager = this.mocker.getInstance(ComponentManager.class, "context");
        when(componentManager.getInstance(Execution.class)).thenReturn(execution);
        doReturn(executionContext).when(execution).getContext();
        doReturn(mock(XWikiContext.class)).when(executionContext).getProperty("xwikicontext");
        this.patientResource = (DefaultPatientResourceImpl) this.mocker.getComponentUnderTest();

        this.logger = this.mocker.getMockedLogger();
        this.repository = this.mocker.getInstance(PatientRepository.class);
        this.access = this.mocker.getInstance(AuthorizationService.class);

        final UserManager users = this.mocker.getInstance(UserManager.class);
        doReturn(this.currentUser).when(users).getCurrentUser();

        this.patientDocument = new DocumentReference("wiki", "data", PATIENT_ID);
        doReturn(this.patient).when(this.repository).get(PATIENT_ID);
        doReturn(this.patientDocument).when(this.patient).getDocumentReference();
        doReturn(true).when(this.access).hasAccess(this.currentUser, Right.EDIT, this.patientDocument);

        when(this.repository.get(PATIENT_ID)).thenReturn(this.patient);
        when(this.patient.getId()).thenReturn(PATIENT_ID);

        doReturn(new URI(URI_STRING)).when(this.uriInfo).getRequestUri();
        ReflectionUtils.setFieldValue(this.patientResource, "uriInfo", this.uriInfo);

        Autolinker autolinker = this.mocker.getInstance(Autolinker.class);
        when(autolinker.forResource(any(Class.class), any(UriInfo.class))).thenReturn(autolinker);
        when(autolinker.withGrantedRight(any(Right.class))).thenReturn(autolinker);
        when(autolinker.withExtraParameters(any(String.class), any(String.class))).thenReturn(autolinker);
        when(autolinker.build()).thenReturn(Collections
            .singletonList(new org.phenotips.rest.model.Link().withAllowedMethods(Collections.singletonList("GET"))
                .withHref(URI_STRING).withRel("self")));
    }

    // ----------------------------Get Patient Tests----------------------------

    @Test
    public void getPatientIgnoresMissingPatient()
    {
        doReturn(null).when(this.repository).get(anyString());

        Response response = this.patientResource.getPatient(PATIENT_ID);

        verify(this.logger).debug("No such patient record: [{}]", PATIENT_ID);
        Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void getPatientRejectsRequestWhenUserDoesNotHaveAccess()
    {
        doReturn(false).when(this.access).hasAccess(this.currentUser, Right.VIEW, this.patientDocument);

        Response response = this.patientResource.getPatient(PATIENT_ID);

        verify(this.logger).debug("View access denied to user [{}] on patient record [{}]", this.currentUser,
            PATIENT_ID);
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    @Test
    public void getPatientNormalBehaviour()
    {
        doReturn(true).when(this.access).hasAccess(this.currentUser, Right.VIEW, this.patientDocument);
        doReturn(new JSONObject()).when(this.patient).toJSON();

        Response response = this.patientResource.getPatient(PATIENT_ID);

        Assert.assertTrue(response.getEntity() instanceof JSONObject);
        JSONObject json = (JSONObject) response.getEntity();
        Assert.assertTrue(json.has("links"));
        JSONArray links = json.getJSONArray("links");
        JSONObject selfLink = null;
        for (int i = 0; i < links.length(); ++i) {
            JSONObject link = links.getJSONObject(i);
            if ("self".equals(link.getString("rel"))) {
                selfLink = link;
            }
        }
        Assert.assertNotNull(selfLink);
        Assert.assertEquals(URI_STRING, selfLink.getString("href"));
        Map<String, List<Object>> actualMap = response.getMetadata();
        Assert.assertThat(actualMap, hasValue(hasItem(MediaType.APPLICATION_JSON_TYPE)));
        Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }

    // ----------------------------Update Patient Tests----------------------------

    @Test
    public void updatePatientIgnoresMissingPatient()
    {
        doReturn(null).when(this.repository).get(anyString());

        WebApplicationException ex = null;
        try {
            this.patientResource.updatePatient("", PATIENT_ID, UPDATE);
        } catch (WebApplicationException temp) {
            ex = temp;
        }

        Assert.assertNotNull("updatePatient did not throw a WebApplicationException as expected "
            + "when the patient could not be found", ex);
        Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
        verify(this.logger).debug("Patient record [{}] doesn't exist yet. It can be created by POST-ing the"
            + " JSON to /rest/patients", PATIENT_ID);
    }

    @Test
    public void updatePatientRejectsRequestWhenUserDoesNotHaveAccess()
    {
        doReturn(false).when(this.access).hasAccess(this.currentUser, Right.EDIT, this.patientDocument);

        WebApplicationException ex = null;
        try {
            this.patientResource.updatePatient("", PATIENT_ID, UPDATE);
        } catch (WebApplicationException temp) {
            ex = temp;
        }

        Assert.assertNotNull("updatePatient did not throw a WebApplicationException as expected "
            + "when the User did not have edit rights", ex);
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
        verify(this.logger).debug("Edit access denied to user [{}] on patient record [{}]", this.currentUser,
            PATIENT_ID);
    }

    @Test
    public void updatePatientThrowsExceptionWhenSentWrongIdInJSON()
    {
        JSONObject json = new JSONObject();
        json.put("id", "!!!!!");
        doReturn(PATIENT_ID).when(this.patient).getId();

        WebApplicationException ex = null;
        try {
            this.patientResource.updatePatient(json.toString(), PATIENT_ID, UPDATE);
        } catch (WebApplicationException temp) {
            ex = temp;
        }

        Assert.assertNotNull("updatePatient did not throw a WebApplicationException as expected "
            + "when json id did not match patient id", ex);
        Assert.assertEquals(Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    public void updatePatientCatchesExceptions()
    {
        JSONObject json = new JSONObject();
        json.put("id", PATIENT_ID);
        doReturn(PATIENT_ID).when(this.patient).getId();
        doThrow(Exception.class).when(this.patient).updateFromJSON(any(JSONObject.class),
            any(PatientWritePolicy.class));

        WebApplicationException ex = null;
        try {
            this.patientResource.updatePatient(json.toString(), PATIENT_ID, UPDATE);
        } catch (WebApplicationException temp) {
            ex = temp;
        }

        Assert.assertNotNull("updatePatient did not throw a WebApplicationException as expected "
            + "when catching an Exception from Patient.updateFromJSON", ex);
        Assert.assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getResponse().getStatus());
        verify(this.logger).warn("Failed to update patient [{}] from JSON: {}. Source JSON was: {}",
            this.patient.getId(),
            ex.getMessage(), json.toString());
    }

    @Test
    public void updatePatientNormalBehaviour()
    {
        JSONObject json = new JSONObject();
        json.put("id", PATIENT_ID);
        doReturn(PATIENT_ID).when(this.patient).getId();

        Response response = this.patientResource.updatePatient(json.toString(), PATIENT_ID, UPDATE);

        verify(this.patient).updateFromJSON(any(JSONObject.class), any(PatientWritePolicy.class));
        Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    // ----------------------------Delete Patient Tests----------------------------

    @Test
    public void deletePatientIgnoresMissingPatient()
    {
        doReturn(null).when(this.repository).get(anyString());

        Response response = this.patientResource.deletePatient(PATIENT_ID);

        verify(this.logger).debug("Patient record [{}] didn't exist", PATIENT_ID);
        Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void deletePatientRejectsRequestWhenUserDoesNotHaveAccess()
    {
        doReturn(false).when(this.access).hasAccess(this.currentUser, Right.DELETE, this.patientDocument);

        Response response = this.patientResource.deletePatient(PATIENT_ID);

        verify(this.logger).debug("Delete access denied to user [{}] on patient record [{}]", this.currentUser,
            PATIENT_ID);
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    @Test
    public void deletePatientCatchesException() throws WebApplicationException
    {
        doReturn(true).when(this.access).hasAccess(this.currentUser, Right.DELETE, this.patientDocument);
        doThrow(Exception.class).when(this.repository).delete(this.patient);

        WebApplicationException ex = null;
        try {
            this.patientResource.deletePatient(PATIENT_ID);
        } catch (WebApplicationException temp) {
            ex = temp;
        }

        Assert.assertNotNull("deletePatient did not throw a WebApplicationException as expected "
            + "when catching an Exception", ex);
        Assert.assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getResponse().getStatus());
        verify(this.logger).warn(eq("Failed to delete patient record [{}]: {}"), eq(PATIENT_ID), anyString());
    }

    @Test
    public void deletePatientNormalBehaviour() throws WebApplicationException
    {
        doReturn(true).when(this.access).hasAccess(this.currentUser, Right.DELETE, this.patientDocument);

        Response response = this.patientResource.deletePatient(PATIENT_ID);

        verify(this.repository).delete(this.patient);
        Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    // ----------------------------Patch Patient Tests-----------------------------

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithNullIdThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(EMPTY_JSON, null);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithEmptyIdThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(EMPTY_JSON, StringUtils.EMPTY);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithBlankIdThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(EMPTY_JSON, StringUtils.SPACE);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithNullJsonThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(null, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithEmptyJsonThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(StringUtils.EMPTY, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientWithBlankJsonThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient(StringUtils.SPACE, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientNoPatientWithSpecifiedIdExistsResultsInWebApplicationException()
    {
        try {
            when(this.repository.get(PATIENT_ID)).thenReturn(null);
            this.patientResource.patchPatient(EMPTY_JSON, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientUserHasNoEditAccessThrowsWebApplicationException()
    {
        try {
            when(this.access.hasAccess(this.currentUser, Right.EDIT, this.patientDocument)).thenReturn(false);
            this.patientResource.patchPatient(EMPTY_JSON, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientInvalidJsonThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient("[]", PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientIdFromJsonAndPatientIdConflictThrowsWebApplicationException()
    {
        try {
            this.patientResource.patchPatient("{\"id\":\"wrong\"}", PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test(expected = WebApplicationException.class)
    public void patchPatientUpdatingPatientFromJsonFailsResultsInWebApplicationException()
    {
        try {
            doThrow(new RuntimeException()).when(this.patient).updateFromJSON(any(JSONObject.class),
                eq(PatientWritePolicy.MERGE));
            this.patientResource.patchPatient(EMPTY_JSON, PATIENT_ID);
        } catch (final WebApplicationException ex) {
            Assert.assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getResponse().getStatus());
            throw ex;
        }
    }

    @Test
    public void patchPatientUpdatesPatientSuccessfully()
    {
        final Response response = this.patientResource.patchPatient(EMPTY_JSON, PATIENT_ID);
        verify(this.patient, times(1)).updateFromJSON(any(JSONObject.class), eq(PatientWritePolicy.MERGE));
        Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
}
