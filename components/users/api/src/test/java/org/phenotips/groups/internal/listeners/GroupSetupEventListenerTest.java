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
package org.phenotips.groups.internal.listeners;

import org.phenotips.groups.Group;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.internal.DefaultExecution;
import org.xwiki.model.EntityType;
import org.xwiki.model.internal.DefaultModelConfiguration;
import org.xwiki.model.internal.DefaultModelContext;
import org.xwiki.model.internal.reference.DefaultEntityReferenceValueProvider;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.internal.reference.RelativeStringEntityReferenceResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.QueryException;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.util.List;

import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.model.reference.CurrentEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentMixedEntityReferenceValueProvider;
import com.xpn.xwiki.internal.model.reference.CurrentMixedStringDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentReferenceDocumentReferenceResolver;
import com.xpn.xwiki.internal.model.reference.CurrentReferenceEntityReferenceResolver;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.Utils;

import net.jcip.annotations.NotThreadSafe;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@NotThreadSafe
@ComponentList({ LocalStringEntityReferenceSerializer.class, RelativeStringEntityReferenceResolver.class,
    CurrentReferenceDocumentReferenceResolver.class, CurrentReferenceEntityReferenceResolver.class,
    CurrentEntityReferenceValueProvider.class, DefaultModelContext.class, DefaultExecution.class,
    DefaultModelConfiguration.class, CurrentMixedStringDocumentReferenceResolver.class,
    CurrentMixedEntityReferenceValueProvider.class, DefaultEntityReferenceValueProvider.class })
public class GroupSetupEventListenerTest
{
    private static final EntityReference GROUPS_CLASS_REFERENCE =
        new EntityReference("XWikiGroups", EntityType.DOCUMENT,
            new EntityReference("XWiki", EntityType.SPACE));

    private static final EntityReference RIGHTS_CLASS_REFERENCE =
        new EntityReference("XWikiRights", EntityType.DOCUMENT,
            new EntityReference("XWiki", EntityType.SPACE));

    @Rule
    public final MockitoComponentMockingRule<EventListener> mocker = new MockitoComponentMockingRule<>(
        GroupSetupEventListener.class);

    @Test
    public void getName() throws ComponentLookupException, QueryException
    {
        Assert.assertTrue(StringUtils.isNotEmpty(this.mocker.getComponentUnderTest().getName()));
    }

    @Test
    public void getEvents() throws ComponentLookupException, QueryException
    {
        List<Event> events = this.mocker.getComponentUnderTest().getEvents();
        Assert.assertFalse(events.isEmpty());
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0) instanceof DocumentCreatingEvent);
    }

    @Test
    public void onEvent() throws ComponentLookupException, XWikiException
    {
        Utils.setComponentManager(this.mocker);

        DocumentReference docReference = new DocumentReference("xwiki", "Groups", "Group1");
        Provider<XWikiContext> contextProvider = this.mocker.getInstance(XWikiContext.TYPE_PROVIDER);
        XWikiContext context = mock(XWikiContext.class);
        when(contextProvider.get()).thenReturn(context);
        XWiki xwiki = mock(XWiki.class);
        when(context.getWiki()).thenReturn(xwiki);

        XWikiDocument doc = mock(XWikiDocument.class);
        when(doc.getDocumentReference()).thenReturn(docReference);
        BaseObject groupObject = new BaseObject();
        BaseObject rightsObject = new BaseObject();
        when(doc.newXObject(eq(GroupSetupEventListenerTest.GROUPS_CLASS_REFERENCE), eq(context)))
            .thenReturn(groupObject);
        when(doc.newXObject(eq(GroupSetupEventListenerTest.RIGHTS_CLASS_REFERENCE), eq(context)))
            .thenReturn(rightsObject);
        when(doc.getXObject(Group.CLASS_REFERENCE)).thenReturn(mock(BaseObject.class));

        DocumentReference adminsDocReference = new DocumentReference("xwiki", "Groups", "Group1 Managers");
        XWikiDocument adminsDoc = mock(XWikiDocument.class);
        BaseObject adminsGroupObject = new BaseObject();
        BaseObject adminsRightsObject = new BaseObject();
        when(adminsDoc.newXObject(eq(GroupSetupEventListenerTest.GROUPS_CLASS_REFERENCE), eq(context)))
            .thenReturn(adminsGroupObject);
        when(adminsDoc.newXObject(eq(GroupSetupEventListenerTest.RIGHTS_CLASS_REFERENCE), eq(context)))
            .thenReturn(adminsRightsObject);
        when(xwiki.getDocument(eq(adminsDocReference), eq(context))).thenReturn(adminsDoc);

        DocumentAccessBridge dab = this.mocker.getInstance(DocumentAccessBridge.class);

        DocumentReference userReference = new DocumentReference("xwiki", "XWiki", "User");
        when(dab.getCurrentUserReference()).thenReturn(userReference);

        this.mocker.getComponentUnderTest().onEvent(new DocumentCreatingEvent(docReference), doc, context);

        Mockito.verify(xwiki).saveDocument(eq(adminsDoc), any(String.class), eq(true), eq(context));
        Mockito.verify(xwiki, Mockito.never())
            .saveDocument(eq(doc), any(String.class), any(Boolean.class), eq(context));

        Assert.assertEquals(1, rightsObject.getIntValue("allow"));
        Assert.assertEquals("edit", rightsObject.getStringValue("levels"));
        Assert.assertEquals("xwiki:Groups.Group1 Managers", rightsObject.getLargeStringValue("groups"));
        Assert.assertEquals("", rightsObject.getLargeStringValue("users"));

        Assert.assertEquals(1, adminsRightsObject.getIntValue("allow"));
        Assert.assertEquals("edit", adminsRightsObject.getStringValue("levels"));
        Assert.assertEquals("xwiki:Groups.Group1 Managers", adminsRightsObject.getLargeStringValue("groups"));
        Assert.assertEquals("", adminsRightsObject.getLargeStringValue("users"));

        Assert.assertEquals("xwiki:Groups.Group1 Managers", groupObject.getStringValue("member"));
        Assert.assertEquals("xwiki:XWiki.User", adminsGroupObject.getStringValue("member"));
    }

    @Test
    public void onEventWithNonGroup() throws ComponentLookupException, XWikiException
    {
        Utils.setComponentManager(this.mocker);

        DocumentReference docReference = new DocumentReference("xwiki", "Groups", "Group1");
        Provider<XWikiContext> contextProvider = this.mocker.getInstance(XWikiContext.TYPE_PROVIDER);
        XWikiContext context = mock(XWikiContext.class);
        when(contextProvider.get()).thenReturn(context);
        XWikiDocument doc = mock(XWikiDocument.class);
        when(doc.getXObject(Group.CLASS_REFERENCE)).thenReturn(null);

        this.mocker.getComponentUnderTest().onEvent(new DocumentCreatingEvent(docReference), doc, context);

        Mockito.verifyZeroInteractions(context);
        Mockito.verify(doc).getXObject(Group.CLASS_REFERENCE);
        Mockito.verifyNoMoreInteractions(doc);
    }

    @Test
    public void onEventWithTemplate() throws ComponentLookupException, XWikiException
    {
        Utils.setComponentManager(this.mocker);

        DocumentReference docReference = new DocumentReference("xwiki", "PhenoTips", "PhenoTipsGroupTemplate");
        Provider<XWikiContext> contextProvider = this.mocker.getInstance(XWikiContext.TYPE_PROVIDER);
        XWikiContext context = mock(XWikiContext.class);
        when(contextProvider.get()).thenReturn(context);
        XWikiDocument doc = mock(XWikiDocument.class);
        when(doc.getXObject(Group.CLASS_REFERENCE)).thenReturn(mock(BaseObject.class));
        when(doc.getDocumentReference()).thenReturn(docReference);

        this.mocker.getComponentUnderTest().onEvent(new DocumentCreatingEvent(docReference), doc, context);

        Mockito.verifyZeroInteractions(context);
        Mockito.verify(doc).getXObject(Group.CLASS_REFERENCE);
        Mockito.verify(doc).getDocumentReference();
        Mockito.verifyNoMoreInteractions(doc);
    }

    @Test
    public void onEventWithExceptions() throws ComponentLookupException, XWikiException
    {
        Utils.setComponentManager(this.mocker);

        DocumentReference docReference = new DocumentReference("xwiki", "Groups", "Group1");
        Provider<XWikiContext> contextProvider = this.mocker.getInstance(XWikiContext.TYPE_PROVIDER);
        XWikiContext context = mock(XWikiContext.class);
        when(contextProvider.get()).thenReturn(context);
        XWiki xwiki = mock(XWiki.class);
        when(context.getWiki()).thenReturn(xwiki);

        XWikiDocument doc = mock(XWikiDocument.class);
        when(doc.getDocumentReference()).thenReturn(docReference);
        when(doc.getXObject(Group.CLASS_REFERENCE)).thenReturn(mock(BaseObject.class));

        DocumentReference adminsDocReference = new DocumentReference("xwiki", "Groups", "Group1 Managers");
        when(xwiki.getDocument(eq(adminsDocReference), eq(context))).thenThrow(new XWikiException(0, 0, "DB Error"));

        DocumentAccessBridge dab = this.mocker.getInstance(DocumentAccessBridge.class);

        DocumentReference userReference = new DocumentReference("xwiki", "XWiki", "User");
        when(dab.getCurrentUserReference()).thenReturn(userReference);

        this.mocker.getComponentUnderTest().onEvent(new DocumentCreatingEvent(docReference), doc, context);

        Mockito.verify(xwiki, Mockito.never())
            .saveDocument(eq(doc), any(String.class), any(Boolean.class), eq(context));

        Mockito.verify(doc).getXObject(Group.CLASS_REFERENCE);
        Mockito.verify(doc).getDocumentReference();
        Mockito.verifyNoMoreInteractions(doc);
    }
}
