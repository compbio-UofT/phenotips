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
package org.phenotips.vocabularies.rest.internal;

import org.phenotips.rest.Autolinker;
import org.phenotips.vocabularies.rest.CategoryTermSuggestionsResource;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link DefaultCategoryTermSuggestionsResource} class.
 */
public class DefaultCategoryTermSuggestionsResourceTest
{
    private static final String ID_LABEL = "id";

    private static final String CATEGORY_A = "categoryA";

    private static final String TERM_PARTIAL = "term";

    private static final String DISEASE_CATEGORY = "disease";

    private static final String TERM_1_ID = "term1";

    private static final String TERM_2_ID = "term2";

    private static final String TERM_3_ID = "term3";

    @Rule
    public MockitoComponentMockingRule<CategoryTermSuggestionsResource> mocker =
        new MockitoComponentMockingRule<>(DefaultCategoryTermSuggestionsResource.class);

    private CategoryTermSuggestionsResource component;

    private VocabularyManager vm;

    private Logger logger;

    @Mock
    private VocabularyTerm term1;

    @Mock
    private VocabularyTerm term2;

    @Mock
    private VocabularyTerm term3;

    private Autolinker autolinker;

    @Mock
    private UriInfo uriInfo;

    @Before
    public void setUp() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        final Execution execution = mock(Execution.class);
        final ExecutionContext executionContext = mock(ExecutionContext.class);
        final ComponentManager componentManager = this.mocker.getInstance(ComponentManager.class, "context");
        when(componentManager.getInstance(Execution.class)).thenReturn(execution);
        when(execution.getContext()).thenReturn(executionContext);
        when(executionContext.getProperty("xwikicontext")).thenReturn(mock(XWikiContext.class));

        ReflectionUtils.setFieldValue(this.mocker.getComponentUnderTest(), "uriInfo", this.uriInfo);

        this.component = this.mocker.getComponentUnderTest();
        this.vm = this.mocker.getInstance(VocabularyManager.class);
        this.logger = this.mocker.getMockedLogger();

        when(this.term1.toJSON()).thenReturn(new JSONObject().put("id", TERM_1_ID));
        when(this.term2.toJSON()).thenReturn(new JSONObject().put("id", TERM_2_ID));
        when(this.term3.toJSON()).thenReturn(new JSONObject().put("id", TERM_3_ID));

        this.autolinker = this.mocker.getInstance(Autolinker.class);
        when(this.autolinker.forSecondaryResource(any(Class.class), eq(this.uriInfo))).thenReturn(this.autolinker);
        when(this.autolinker.forResource(any(Class.class), eq(this.uriInfo))).thenReturn(this.autolinker);
        when(this.autolinker.withActionableResources(any(Class.class))).thenReturn(this.autolinker);
        when(this.autolinker.withExtraParameters(anyString(), anyString())).thenReturn(this.autolinker);
        when(this.autolinker.build()).thenReturn(Collections.emptyList());

        when(this.vm.getAvailableCategories()).thenReturn(Collections.singletonList(DISEASE_CATEGORY));
    }

    @Test(expected = WebApplicationException.class)
    public void suggestThrowsExceptionWhenInputIsBlank() throws Exception
    {
        this.component.suggest(CATEGORY_A, " ", 11);
        verify(this.logger).error("Both input and category must be provided.");
        Assert.fail("An exception should be thrown if input is blank.");
    }

    @Test(expected = WebApplicationException.class)
    public void suggestThrowsExceptionWhenCategoryIsBlank() throws Exception
    {
        this.component.suggest("", "Abc", 11);
        verify(this.logger).error("Both input and category must be provided.");
        Assert.fail("An exception should be thrown if category is blank.");
    }

    @Test(expected = WebApplicationException.class)
    public void suggestThrowsExceptionWhenCategoryIsNotValid() throws Exception
    {
        when(this.vm.hasCategory(anyString())).thenReturn(false);
        this.component.suggest(CATEGORY_A, "Abc", 11);
        verify(this.logger).error("The requested vocabulary category [{}] does not exist.", CATEGORY_A);
        Assert.fail("An exception should be thrown if an invalid category is provided.");
    }

    @Test
    public void suggestWorksAsExpectedWithValidData()
    {
        final List<VocabularyTerm> vocabularyTerms = Arrays.asList(this.term1, this.term2, this.term3);
        when(this.vm.hasCategory(DISEASE_CATEGORY)).thenReturn(true);
        when(this.vm.search(TERM_PARTIAL, DISEASE_CATEGORY, 3)).thenReturn(vocabularyTerms);
        final Response response = this.component.suggest(DISEASE_CATEGORY, TERM_PARTIAL, 3);
        final JSONObject entity = (JSONObject) response.getEntity();
        final JSONArray rows = entity.getJSONArray("rows");
        Assert.assertEquals(3, rows.length());
        Assert.assertEquals(TERM_1_ID, rows.getJSONObject(0).getString(ID_LABEL));
        Assert.assertEquals(TERM_2_ID, rows.getJSONObject(1).getString(ID_LABEL));
        Assert.assertEquals(TERM_3_ID, rows.getJSONObject(2).getString(ID_LABEL));
    }
}
