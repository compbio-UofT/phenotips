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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.phenotips.ontology.internal.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

/**
 * @since 1.2RC1
 * @version $Id$
 */
public abstract class AbstractCSVSolrOntologyService extends AbstractSolrOntologyService
{
    /**
     * The name of the Alternative ID field, used for older aliases of updated HPO terms.
     */
    protected static final String ALTERNATIVE_ID_FIELD_NAME = "alt_id";

    protected static final String VERSION_FIELD_NAME = "version";

    protected static final String SIZE_FIELD_NAME = "size";

    protected static final String ROWS_FIELD_NAME = "rows";

    protected static final String SYMBOL_FIELD_NAME = "symbol";

    protected static final String PREV_SYMBOL_FIELD_NAME = "prev_symbol";

    protected static final String ALIAS_SYMBOL_FIELD_NAME = "alias_symbol";

    /** The number of documents to be added and committed to Solr at a time. */
    protected abstract int getSolrDocsPerBatch();

    protected abstract Collection<SolrInputDocument> transform(Map<String, Double> fieldSelection);

    @Override
    public int reindex(String ontologyUrl)
    {
        this.clear();
        return this.index(ontologyUrl);
    }

    /**
     * Add an ontology to the index.
     *
     * @param ontologyUrl the address from where to get the ontology file
     * @return {@code 0} if the indexing succeeded, {@code 1} if writing to the Solr server failed, {@code 2} if the
     *         specified URL is invalid
     */
    protected int index(String ontologyUrl)
    {
        Map<String, Double> fieldSelection = new HashMap<String, Double>();
        Collection<SolrInputDocument> data = transform(fieldSelection);
        if (data == null) {
            return 2;
        }
        try {
            Collection<SolrInputDocument> termBatch = new HashSet<SolrInputDocument>();
            Iterator<SolrInputDocument> dataIterator = data.iterator();
            int batchCounter = 0;
            while (dataIterator.hasNext()) {
                /* Resetting when the batch fills */
                if (batchCounter == getSolrDocsPerBatch()) {
                    commitTerms(termBatch);
                    termBatch = new HashSet<>();
                    batchCounter = 0;
                }
                SolrInputDocument item = dataIterator.next();
                termBatch.add(item);
                batchCounter++;
            }
            commitTerms(termBatch);
            return 0;
        } catch (SolrServerException ex) {
            this.logger.warn("Failed to index ontology: {}", ex.getMessage());
        } catch (IOException ex) {
            this.logger.warn("Failed to communicate with the Solr server while indexing ontology: {}", ex.getMessage());
        } catch (OutOfMemoryError ex) {
            this.logger.warn("Failed to add terms to the Solr. Ran out of memory. {}", ex.getMessage());
        }
        return 1;
    }

    protected void commitTerms(Collection<SolrInputDocument> batch)
        throws SolrServerException, IOException, OutOfMemoryError
    {
        this.externalServicesAccess.getServer().add(batch);
        this.externalServicesAccess.getServer().commit();
        this.externalServicesAccess.getCache().removeAll();
    }

    /**
     * Delete all the data in the Solr index.
     *
     * @return {@code 0} if the command was successful, {@code 1} otherwise
     */
    protected int clear()
    {
        try {
            this.externalServicesAccess.getServer().deleteByQuery("*:*");
            return 0;
        } catch (SolrServerException ex) {
            this.logger.error("SolrServerException while clearing the Solr index", ex);
        } catch (IOException ex) {
            this.logger.error("IOException while clearing the Solr index", ex);
        }
        return 1;
    }

    @Override
    public String getVersion()
    {
        QueryResponse response;
        SolrQuery query = new SolrQuery();
        SolrDocumentList termList;
        SolrDocument firstDoc;

        query.setQuery("version:*");
        query.set(ROWS_FIELD_NAME, "1");
        try {
            response = this.externalServicesAccess.getServer().query(query);
            termList = response.getResults();

            if (!termList.isEmpty()) {
                firstDoc = termList.get(0);
                return firstDoc.getFieldValue(VERSION_FIELD_NAME).toString();
            }
        } catch (SolrServerException | SolrException ex) {
            this.logger.warn("Failed to query ontology version: {}", ex.getMessage());
        } catch (IOException ex) {
            this.logger.error("IOException while getting ontology version", ex);
        }
        return null;
    }

    @Override
    public long size()
    {
        QueryResponse response;
        SolrQuery query = new SolrQuery();
        SolrDocumentList termList;
        SolrDocument firstDoc;

        query.setQuery("size:*");
        query.set(ROWS_FIELD_NAME, "1");
        try {
            response = this.externalServicesAccess.getServer().query(query);
            termList = response.getResults();

            if (!termList.isEmpty()) {
                firstDoc = termList.get(0);
                String result = firstDoc.getFieldValue(SIZE_FIELD_NAME).toString();
                return Long.valueOf(result).longValue();
            }
        } catch (SolrServerException | SolrException ex) {
            this.logger.warn("Failed to query ontology size", ex.getMessage());
        } catch (IOException ex) {
            this.logger.error("IOException while getting ontology size", ex);
        }
        return 0;
    }

}
