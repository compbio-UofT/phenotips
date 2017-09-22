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
package org.phenotips.data.permissions.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.internal.access.EditAccessLevel;
import org.phenotips.data.permissions.internal.access.NoAccessLevel;
import org.phenotips.data.permissions.internal.access.OwnerAccessLevel;
import org.phenotips.data.permissions.internal.access.ViewAccessLevel;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiGroupService;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@link EntityAccessHelper} implementation, {@link DefaultEntityAccessHelper}.
 *
 * @version $Id$
 */
public class DefaultEntityAccessHelperTest
{
    /** The patient used for tests. */
    private static final DocumentReference PATIENT_REFERENCE = new DocumentReference("xwiki", "data", "P0000001");

    /** The user used as the owner of the patient. */
    private static final DocumentReference OWNER = new DocumentReference("xwiki", "XWiki", "padams");

    private static final EntityReference RELATIVE_OWNER =
        new EntityReference("padams", EntityType.DOCUMENT, Patient.DEFAULT_DATA_SPACE);

    private static final String OWNER_STR = "xwiki:XWiki.padams";

    /** The user used as a collaborator. */
    private static final DocumentReference COLLABORATOR = new DocumentReference("xwiki", "XWiki", "hmccoy");

    private static final EntityReference RELATIVE_COLLABORATOR =
        new EntityReference("hmccoy", EntityType.DOCUMENT, Patient.DEFAULT_DATA_SPACE);

    private static final String COLLABORATOR_STR = "xwiki:XWiki.hmccoy";

    /** The user used as a non-collaborator. */
    private static final DocumentReference OTHER_USER = new DocumentReference("xwiki", "XWiki", "cxavier");

    private static final String OTHER_USER_STR = "xwiki:XWiki.cxavier";

    /** Group used as collaborator. */
    private static final DocumentReference GROUP = new DocumentReference("xwiki", "XWiki", "collaborators");

    private static final String GROUP_STR = "xwiki:XWiki.collaborators";

    private static final DocumentReference OWNER_CLASS = new DocumentReference("xwiki", "PhenoTips", "Owner");

    private static final DocumentReference VISIBILITY_CLASS = new DocumentReference("xwiki", "PhenoTips", "Visibility");

    private static final DocumentReference COLLABORATOR_CLASS = new DocumentReference("xwiki", "PhenoTips",
        "Collaborator");

    @Rule
    public final MockitoComponentMockingRule<EntityAccessHelper> mocker =
        new MockitoComponentMockingRule<>(DefaultEntityAccessHelper.class);

    private Patient patient = mock(Patient.class);

    private XWikiDocument patientDoc = mock(XWikiDocument.class);

    private BaseObject ownerObject = mock(BaseObject.class);

    private BaseObject visibilityObject = mock(BaseObject.class);

    private ParameterizedType entityResolverType = new DefaultParameterizedType(null, DocumentReferenceResolver.class,
        EntityReference.class);

    private DocumentReferenceResolver<EntityReference> partialEntityResolver;

    private ParameterizedType stringResolverType = new DefaultParameterizedType(null, DocumentReferenceResolver.class,
        String.class);

    private DocumentReferenceResolver<String> stringEntityResolver;

    private ParameterizedType stringSerializerType = new DefaultParameterizedType(null,
        EntityReferenceSerializer.class, String.class);

    private EntityReferenceSerializer<String> stringEntitySerializer;

    private DocumentAccessBridge bridge;

    private XWikiContext context;

    private XWiki xwiki;

    @Before
    public void setup() throws ComponentLookupException
    {
        this.bridge = this.mocker.getInstance(DocumentAccessBridge.class);
        this.partialEntityResolver = this.mocker.getInstance(this.entityResolverType, "currentmixed");
        this.stringEntityResolver = this.mocker.getInstance(this.stringResolverType, "currentmixed");
        this.stringEntitySerializer = this.mocker.getInstance(this.stringSerializerType);

        when(this.partialEntityResolver.resolve(Owner.CLASS_REFERENCE, PATIENT_REFERENCE)).thenReturn(
            OWNER_CLASS);
        when(this.partialEntityResolver.resolve(Visibility.CLASS_REFERENCE, PATIENT_REFERENCE)).thenReturn(
            VISIBILITY_CLASS);
        when(this.partialEntityResolver.resolve(Collaborator.CLASS_REFERENCE, PATIENT_REFERENCE)).thenReturn(
            COLLABORATOR_CLASS);

        when(this.partialEntityResolver.resolve(OWNER)).thenReturn(OWNER);
        when(this.partialEntityResolver.resolve(RELATIVE_OWNER)).thenReturn(OWNER);
        when(this.partialEntityResolver.resolve(COLLABORATOR)).thenReturn(COLLABORATOR);
        when(this.partialEntityResolver.resolve(RELATIVE_COLLABORATOR)).thenReturn(COLLABORATOR);

        when(this.stringEntityResolver.resolve(OWNER_STR)).thenReturn(OWNER);
        when(this.stringEntityResolver.resolve(OWNER_STR, PATIENT_REFERENCE)).thenReturn(OWNER);
        when(this.stringEntityResolver.resolve(COLLABORATOR_STR, PATIENT_REFERENCE)).thenReturn(COLLABORATOR);
        when(this.stringEntityResolver.resolve(OTHER_USER_STR, PATIENT_REFERENCE)).thenReturn(OTHER_USER);
        when(this.stringEntityResolver.resolve(GROUP_STR, PATIENT_REFERENCE)).thenReturn(GROUP);

        when(this.stringEntitySerializer.serialize(OWNER)).thenReturn(OWNER_STR);
        when(this.stringEntitySerializer.serialize(COLLABORATOR)).thenReturn(COLLABORATOR_STR);
        when(this.stringEntitySerializer.serialize(OTHER_USER)).thenReturn(OTHER_USER_STR);

        Execution e = this.mocker.getInstance(Execution.class);
        ExecutionContext ec = mock(ExecutionContext.class);
        when(e.getContext()).thenReturn(ec);
        this.context = mock(XWikiContext.class);
        when(ec.getProperty("xwikicontext")).thenReturn(this.context);
        this.xwiki = mock(XWiki.class);
        when(this.context.getWiki()).thenReturn(this.xwiki);

        when(this.patient.getDocumentReference()).thenReturn(PATIENT_REFERENCE);
        when(this.patient.getXDocument()).thenReturn(this.patientDoc);
        when(this.patientDoc.getXObject(OWNER_CLASS)).thenReturn(this.ownerObject);
        when(this.patientDoc.getXObject(OWNER_CLASS, true, this.context)).thenReturn(this.ownerObject);
        when(this.ownerObject.getStringValue("owner")).thenReturn(OWNER_STR);
        when(this.patientDoc.getXObject(VISIBILITY_CLASS)).thenReturn(this.visibilityObject);
        when(this.patientDoc.getXObject(VISIBILITY_CLASS, true, this.context)).thenReturn(this.visibilityObject);
    }

    /** Basic tests for {@link EntityAccessHelper#getCurrentUser()}. */
    @Test
    public void getCurrentUser() throws ComponentLookupException
    {
        when(this.bridge.getCurrentUserReference()).thenReturn(OWNER);
        Assert.assertSame(OWNER, this.mocker.getComponentUnderTest().getCurrentUser());
    }

    /** {@link EntityAccessHelper#getCurrentUser()} returns null for guests. */
    @Test
    public void getCurrentUserForGuest() throws ComponentLookupException
    {
        when(this.bridge.getCurrentUserReference()).thenReturn(null);
        Assert.assertNull(this.mocker.getComponentUnderTest().getCurrentUser());
    }

    /** Basic tests for {@link EntityAccessHelper#getOwner(Patient)}. */
    @Test
    public void getOwner() throws ComponentLookupException
    {
        Assert.assertSame(OWNER, this.mocker.getComponentUnderTest().getOwner(this.patient).getUser());
    }

    /** {@link EntityAccessHelper#getOwner(Patient)} returns a null user when the owner isn't specified. */
    @Test
    public void getOwnerWithMissingOwnerAndReferrer() throws ComponentLookupException
    {
        when(this.ownerObject.getStringValue("owner")).thenReturn(null);
        Assert.assertNull(this.mocker.getComponentUnderTest().getOwner(this.patient).getUser());

        when(this.ownerObject.getStringValue("owner")).thenReturn("");
        Assert.assertNull(this.mocker.getComponentUnderTest().getOwner(this.patient).getUser());

        Mockito.verify(this.patient, Mockito.never()).getReporter();
    }

    /** {@link EntityAccessHelper#getOwner(Patient)} returns {@code null} when the patient is missing. */
    @Test
    public void getOwnerWithMissingPatient() throws ComponentLookupException
    {
        Assert.assertNull(this.mocker.getComponentUnderTest().getOwner(null));
    }

    /** {@link EntityAccessHelper#getOwner(Patient)} returns {@code null} when the patient is missing. */
    @Test
    public void getOwnerWithMissingDocument() throws ComponentLookupException
    {
        when(this.patient.getDocumentReference()).thenReturn(null);
        Assert.assertNull(this.mocker.getComponentUnderTest().getOwner(this.patient));
    }

    /** Basic tests for {@link EntityAccessHelper#setOwner(Patient, EntityReference)}. */
    @Test
    public void setOwner() throws Exception
    {
        Assert.assertTrue(this.mocker.getComponentUnderTest().setOwner(this.patient, OWNER));
        Mockito.verify(this.ownerObject).set("owner", OWNER_STR, this.context);
        Mockito.verify(this.xwiki).saveDocument(this.patientDoc, "Set owner: " + OWNER_STR, true, this.context);
    }

    /** Basic tests for {@link EntityAccessHelper#setOwner(Patient, EntityReference)}. */
    @Test
    public void setOwnerWithFailure() throws Exception
    {
        Mockito.doThrow(new RuntimeException()).when(this.patientDoc).getXObject(OWNER_CLASS, true, this.context);
        Assert.assertFalse(this.mocker.getComponentUnderTest().setOwner(this.patient, OWNER));
    }

    /** Basic tests for {@link EntityAccessHelper#getVisibility(Patient)}. */
    @Test
    public void getVisibility() throws ComponentLookupException
    {
        when(this.visibilityObject.getStringValue("visibility")).thenReturn("public");
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        Visibility publicV = mock(Visibility.class);
        when(manager.resolveVisibility("public")).thenReturn(publicV);
        Assert.assertSame(publicV, this.mocker.getComponentUnderTest().getVisibility(this.patient));
    }

    /** {@link EntityAccessHelper#getVisibility(Patient)} returns null when the owner isn't specified. */
    @Test
    public void getVisibilityWithMissingVisibility() throws ComponentLookupException
    {
        when(this.visibilityObject.getStringValue("visibility")).thenReturn(null);
        Assert.assertNull(this.mocker.getComponentUnderTest().getVisibility(this.patient));

        when(this.visibilityObject.getStringValue("visibility")).thenReturn("");
        Assert.assertNull(this.mocker.getComponentUnderTest().getVisibility(this.patient));
    }

    /** Basic tests for {@link EntityAccessHelper#setOwner(Patient, EntityReference)}. */
    @Test
    public void setVisibility() throws Exception
    {
        Visibility publicV = mock(Visibility.class);
        when(publicV.getName()).thenReturn("public");
        Assert.assertTrue(this.mocker.getComponentUnderTest().setVisibility(this.patient, publicV));
        Mockito.verify(this.visibilityObject).set("visibility", "public", this.context);
    }

    /** Basic tests for {@link EntityAccessHelper#setOwner(Patient, EntityReference)}. */
    @Test
    public void setVisibilityWithNullVisibility() throws Exception
    {
        Assert.assertTrue(this.mocker.getComponentUnderTest().setVisibility(this.patient, null));
        Mockito.verify(this.visibilityObject).set("visibility", "", this.context);
    }

    /** Basic tests for {@link EntityAccessHelper#setVisibility(Patient, Visibility)}. */
    @Test
    public void setVisibilityWithFailure() throws Exception
    {
        Visibility publicV = mock(Visibility.class);
        when(publicV.getName()).thenReturn("public");
        Mockito.doThrow(new RuntimeException()).when(this.visibilityObject).set(anyString(), any(), eq(this.context));
        Assert.assertFalse(this.mocker.getComponentUnderTest().setVisibility(this.patient, publicV));
    }

    /** Basic tests for {@link EntityAccessHelper#getCollaborators(Patient)}. */
    @Test
    public void getCollaborators() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        List<BaseObject> objects = new ArrayList<>();
        BaseObject collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("edit");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(OTHER_USER_STR);
        when(collaborator.getStringValue("access")).thenReturn("view");
        objects.add(collaborator);
        when(doc.getXObjects(COLLABORATOR_CLASS)).thenReturn(objects);
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        AccessLevel edit = mock(AccessLevel.class);
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        AccessLevel view = mock(AccessLevel.class);
        when(manager.resolveAccessLevel("view")).thenReturn(view);
        Collection<Collaborator> collaborators = this.mocker.getComponentUnderTest().getCollaborators(this.patient);
        Assert.assertEquals(2, collaborators.size());
        Collaborator c = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());
        Assert.assertTrue(collaborators.contains(c));
        c = new DefaultCollaborator(OTHER_USER, view, this.mocker.getComponentUnderTest());
        Assert.assertTrue(collaborators.contains(c));
    }

    /**
     * {@link EntityAccessHelper#getCollaborators(Patient)} returns the most permissive access level when multiple
     * entries are present.
     */
    @Test
    public void getCollaboratorsWithMultipleEntries() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        List<BaseObject> objects = new ArrayList<>();
        BaseObject collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("edit");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("view");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("manage");
        objects.add(collaborator);
        when(doc.getXObjects(COLLABORATOR_CLASS)).thenReturn(objects);
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        AccessLevel edit = mock(AccessLevel.class);
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        AccessLevel view = mock(AccessLevel.class);
        when(manager.resolveAccessLevel("view")).thenReturn(view);
        AccessLevel manage = mock(AccessLevel.class);
        when(manager.resolveAccessLevel("manage")).thenReturn(manage);
        when(view.compareTo(edit)).thenReturn(-10);
        when(manage.compareTo(edit)).thenReturn(10);
        Collection<Collaborator> collaborators = this.mocker.getComponentUnderTest().getCollaborators(this.patient);
        Assert.assertEquals(1, collaborators.size());
        Collaborator c = new DefaultCollaborator(COLLABORATOR, manage, this.mocker.getComponentUnderTest());
        Assert.assertTrue(collaborators.contains(c));
    }

    /** {@link EntityAccessHelper#getCollaborators(Patient)} skips objects with missing values. */
    @Test
    public void getCollaboratorsWithMissingValues() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        List<BaseObject> objects = new ArrayList<>();
        BaseObject collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("");
        objects.add(collaborator);
        objects.add(null);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn("");
        when(collaborator.getStringValue("access")).thenReturn("view");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(null);
        when(collaborator.getStringValue("access")).thenReturn(null);
        objects.add(collaborator);
        when(doc.getXObjects(COLLABORATOR_CLASS)).thenReturn(objects);
        Collection<Collaborator> collaborators = this.mocker.getComponentUnderTest().getCollaborators(this.patient);
        Assert.assertTrue(collaborators.isEmpty());
    }

    /** {@link EntityAccessHelper#getCollaborators(Patient)} returns an empty set when accessing the patient fails. */
    @Test
    public void getCollaboratorsWithException() throws Exception
    {
        when(this.patient.getXDocument()).thenThrow(new RuntimeException());
        Collection<Collaborator> collaborators = this.mocker.getComponentUnderTest().getCollaborators(this.patient);
        Assert.assertNotNull(collaborators);
        Assert.assertTrue(collaborators.isEmpty());
    }

    /** Basic tests for {@link EntityAccessHelper#setCollaborators(Patient, Collection)}. */
    @Test
    public void setCollaborators() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        AccessLevel view = mock(AccessLevel.class);
        when(view.getName()).thenReturn("view");
        when(manager.resolveAccessLevel("view")).thenReturn(view);
        Collection<Collaborator> collaborators = new HashSet<>();
        Collaborator c = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());
        collaborators.add(c);
        c = new DefaultCollaborator(OTHER_USER, view, this.mocker.getComponentUnderTest());
        collaborators.add(c);
        BaseObject o = mock(BaseObject.class);
        when(doc.newXObject(COLLABORATOR_CLASS, this.context)).thenReturn(o);

        Assert.assertTrue(this.mocker.getComponentUnderTest().setCollaborators(this.patient, collaborators));
        Mockito.verify(o).setStringValue("collaborator", COLLABORATOR_STR);
        Mockito.verify(o).setStringValue("access", "edit");
        Mockito.verify(o).setStringValue("collaborator", OTHER_USER_STR);
        Mockito.verify(o).setStringValue("access", "view");
        Mockito.verify(doc).removeXObjects(COLLABORATOR_CLASS);
        Mockito.verify(this.xwiki).saveDocument(doc, "Updated collaborators", true, this.context);
    }

    /**
     * {@link EntityAccessHelper#setCollaborators(Patient, Collection)} returns false when accessing the patient fails.
     */
    @Test
    public void setCollaboratorsWithFailure() throws Exception
    {
        Mockito.doThrow(new RuntimeException()).when(this.patient).getXDocument();
        Collection<Collaborator> collaborators = new HashSet<>();
        Assert.assertFalse(this.mocker.getComponentUnderTest().setCollaborators(this.patient, collaborators));
    }

    /**
     * {@link EntityAccessHelper#addCollaborator(Patient, Collaborator)} adds a new Collaborator object if one doesn't
     * exist already.
     */
    @Test
    public void addCollaboratorWithNewObject() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        BaseObject o = mock(BaseObject.class);
        when(doc.getXObject(COLLABORATOR_CLASS, "collaborator", COLLABORATOR_STR, false)).thenReturn(null);
        when(doc.newXObject(COLLABORATOR_CLASS, this.context)).thenReturn(o);

        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertTrue(this.mocker.getComponentUnderTest().addCollaborator(this.patient, collaborator));
        Mockito.verify(o).setStringValue("collaborator", COLLABORATOR_STR);
        Mockito.verify(o).setStringValue("access", "edit");
        Mockito.verify(this.xwiki).saveDocument(doc, "Added collaborator: " + COLLABORATOR_STR, true, this.context);
    }

    /** {@link EntityAccessHelper#addCollaborator(Patient, Collaborator)} modifies the existing Collaborator object. */
    @Test
    public void addCollaboratorWithExistingObject() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        BaseObject o = mock(BaseObject.class);
        when(doc.getXObject(COLLABORATOR_CLASS, "collaborator", COLLABORATOR_STR, false)).thenReturn(o);

        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertTrue(this.mocker.getComponentUnderTest().addCollaborator(this.patient, collaborator));
        Mockito.verify(o).setStringValue("collaborator", COLLABORATOR_STR);
        Mockito.verify(o).setStringValue("access", "edit");
        Mockito.verify(this.xwiki).saveDocument(doc, "Added collaborator: " + COLLABORATOR_STR, true, this.context);
    }

    /**
     * {@link EntityAccessHelper#addCollaborator(Patient, Collaborator)} returns false when accessing the document
     * fails.
     */
    @Test
    public void addCollaboratorWithFailure() throws Exception
    {
        Mockito.doThrow(new RuntimeException()).when(this.patient).getXDocument();

        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertFalse(this.mocker.getComponentUnderTest().addCollaborator(this.patient, collaborator));
    }

    /** {@link EntityAccessHelper#removeCollaborator(Patient, Collaborator)} removes the existing Collaborator. */
    @Test
    public void removeCollaboratorWithExistingObject() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        BaseObject o = mock(BaseObject.class);
        when(doc.getXObject(COLLABORATOR_CLASS, "collaborator", COLLABORATOR_STR, false)).thenReturn(o);

        AccessLevel edit = mock(AccessLevel.class);
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertTrue(this.mocker.getComponentUnderTest().removeCollaborator(this.patient, collaborator));
        Mockito.verify(doc).removeXObject(o);
        Mockito.verify(this.xwiki).saveDocument(doc, "Removed collaborator: " + COLLABORATOR_STR, true, this.context);
    }

    /** {@link EntityAccessHelper#removeCollaborator(Patient, Collaborator)} does nothing if the object isn't found. */
    @Test
    public void removeCollaboratorWithMissingObject() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(doc.getXObject(COLLABORATOR_CLASS, "collaborator", COLLABORATOR_STR, false)).thenReturn(null);

        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertFalse(this.mocker.getComponentUnderTest().removeCollaborator(this.patient, collaborator));
        Mockito.verify(doc, Mockito.never()).removeXObject(Matchers.any(BaseObject.class));
        Mockito.verify(this.xwiki, Mockito.never()).saveDocument(doc, "Removed collaborator: " + COLLABORATOR_STR, true,
            this.context);
    }

    /**
     * {@link EntityAccessHelper#removeCollaborator(Patient, Collaborator)} returns false when accessing the document
     * fails.
     */
    @Test
    public void removeCollaboratorWithFailure() throws Exception
    {
        Mockito.doThrow(new RuntimeException()).when(this.patient).getXDocument();

        AccessLevel edit = mock(AccessLevel.class);
        when(edit.getName()).thenReturn("edit");
        Collaborator collaborator = new DefaultCollaborator(COLLABORATOR, edit, this.mocker.getComponentUnderTest());

        Assert.assertFalse(this.mocker.getComponentUnderTest().removeCollaborator(this.patient, collaborator));
    }

    /** {@link EntityAccessHelper#getAccessLevel(Patient, EntityReference)} returns no access for guest users. */
    @Test
    public void getAccessLevelWithOwner() throws Exception
    {
        AccessLevel none = new NoAccessLevel();
        AccessLevel owner = new OwnerAccessLevel();
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        when(manager.resolveAccessLevel("owner")).thenReturn(owner);

        XWikiGroupService groupService = mock(XWikiGroupService.class);
        when(this.xwiki.getGroupService(this.context)).thenReturn(groupService);
        when(groupService.getAllGroupsReferencesForMember(COLLABORATOR, 0, 0, this.context))
            .thenReturn(Collections.<DocumentReference>emptyList());

        Assert.assertSame(owner, this.mocker.getComponentUnderTest().getAccessLevel(this.patient, OWNER));
    }

    /** {@link EntityAccessHelper#getAccessLevel(Patient, EntityReference)} returns no access for guest users. */
    @Test
    public void getAccessLevelWithGuestUser() throws ComponentLookupException
    {
        AccessLevel none = new NoAccessLevel();
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        Assert.assertSame(none, this.mocker.getComponentUnderTest().getAccessLevel(this.patient, null));
    }

    /** {@link EntityAccessHelper#getAccessLevel(Patient, EntityReference)} returns no access with missing patient. */
    @Test
    public void getAccessLevelWithMissingPatient() throws ComponentLookupException
    {
        AccessLevel none = new NoAccessLevel();
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        Assert.assertSame(none, this.mocker.getComponentUnderTest().getAccessLevel(null, OTHER_USER));
    }

    /**
     * {@link EntityAccessHelper#getAccessLevel(Patient, EntityReference)} returns no access with missing patient and
     * user.
     */
    @Test
    public void getAccessLevelWithMissingPatientAndGuestUser() throws ComponentLookupException
    {
        AccessLevel none = new NoAccessLevel();
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        Assert.assertSame(none, this.mocker.getComponentUnderTest().getAccessLevel(null, null));
    }

    /** {@link EntityAccess#getAccessLevel()} returns the specified access for a registered collaborator. */
    @Test
    public void getAccessLevelWithSpecifiedCollaborator() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        List<BaseObject> objects = new ArrayList<>();
        BaseObject collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(COLLABORATOR_STR);
        when(collaborator.getStringValue("access")).thenReturn("edit");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(OTHER_USER_STR);
        when(collaborator.getStringValue("access")).thenReturn("view");
        objects.add(collaborator);
        when(doc.getXObjects(COLLABORATOR_CLASS)).thenReturn(objects);
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        AccessLevel edit = new EditAccessLevel();
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        AccessLevel view = new ViewAccessLevel();
        when(manager.resolveAccessLevel("view")).thenReturn(view);
        AccessLevel none = new NoAccessLevel();
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        XWikiGroupService groupService = mock(XWikiGroupService.class);
        when(this.xwiki.getGroupService(this.context)).thenReturn(groupService);
        when(groupService.getAllGroupsReferencesForMember(COLLABORATOR, 0, 0, this.context))
            .thenReturn(Collections.<DocumentReference>emptyList());

        Assert.assertSame(edit, this.mocker.getComponentUnderTest().getAccessLevel(this.patient, COLLABORATOR));
    }

    /** {@link EntityAccess#getAccessLevel()} returns the specified access for a registered collaborator. */
    @Test
    public void getAccessLevelWithGroupMemberCollaborator() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.patient.getXDocument()).thenReturn(doc);

        List<BaseObject> objects = new ArrayList<>();
        BaseObject collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(GROUP_STR);
        when(collaborator.getStringValue("access")).thenReturn("edit");
        objects.add(collaborator);
        collaborator = mock(BaseObject.class);
        when(collaborator.getStringValue("collaborator")).thenReturn(OTHER_USER_STR);
        when(collaborator.getStringValue("access")).thenReturn("view");
        objects.add(collaborator);
        when(doc.getXObjects(COLLABORATOR_CLASS)).thenReturn(objects);
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        AccessLevel edit = new EditAccessLevel();
        when(manager.resolveAccessLevel("edit")).thenReturn(edit);
        AccessLevel view = new ViewAccessLevel();
        when(manager.resolveAccessLevel("view")).thenReturn(view);
        AccessLevel none = new NoAccessLevel();
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        XWikiGroupService groupService = mock(XWikiGroupService.class);
        when(this.xwiki.getGroupService(this.context)).thenReturn(groupService);
        when(groupService.getAllGroupsReferencesForMember(COLLABORATOR, 0, 0, this.context))
            .thenReturn(Arrays.asList(GROUP));

        Assert.assertSame(edit, this.mocker.getComponentUnderTest().getAccessLevel(this.patient, COLLABORATOR));
    }

    /**
     * {@link EntityAccessHelper#getAccessLevel(Patient, EntityReference)} returns no access when XWiki throws
     * exceptions.
     */
    @Test
    public void getAccessLevelWithExceptions() throws ComponentLookupException, XWikiException
    {
        AccessLevel none = new NoAccessLevel();
        EntityPermissionsManager manager = this.mocker.getInstance(EntityPermissionsManager.class);
        when(manager.resolveAccessLevel("none")).thenReturn(none);
        when(this.xwiki.getGroupService(this.context)).thenThrow(new XWikiException());
        Assert.assertSame(none, this.mocker.getComponentUnderTest().getAccessLevel(this.patient, OTHER_USER));
    }

    /** Basic tests for {@link EntityAccessHelper#getType(EntityReference)}. */
    @Test
    public void getType() throws Exception
    {
        XWikiDocument doc = mock(XWikiDocument.class);
        when(this.bridge.getDocument(OWNER)).thenReturn(doc);
        when(doc.getXObject(new EntityReference("XWikiUsers", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE)))).thenReturn(mock(BaseObject.class));

        doc = mock(XWikiDocument.class);
        when(this.bridge.getDocument(GROUP)).thenReturn(doc);
        when(doc.getXObject(new EntityReference("XWikiUsers", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE)))).thenReturn(null);
        when(doc.getXObject(new EntityReference("XWikiGroups", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE)))).thenReturn(mock(BaseObject.class));

        doc = mock(XWikiDocument.class);
        when(this.bridge.getDocument(COLLABORATOR)).thenReturn(doc);
        when(doc.getXObject(new EntityReference("XWikiUsers", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE)))).thenReturn(null);
        when(doc.getXObject(new EntityReference("XWikiGroups", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE)))).thenReturn(null);

        Assert.assertEquals("user", this.mocker.getComponentUnderTest().getType(OWNER));
        Assert.assertEquals("group", this.mocker.getComponentUnderTest().getType(GROUP));
        Assert.assertEquals("unknown", this.mocker.getComponentUnderTest().getType(COLLABORATOR));
    }
}