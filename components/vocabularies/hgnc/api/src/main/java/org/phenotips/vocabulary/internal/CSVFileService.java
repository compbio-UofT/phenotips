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
package org.phenotips.vocabulary.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.internal.csv.CSVParser;
import org.apache.solr.internal.csv.CSVStrategy;
import org.slf4j.Logger;

/**
 * Parses the CSF input with first line as a headers row into the collection of SolrInputDocuments.
 *
 * @version $Id$
 * @since 1.2RC1
 */
public class CSVFileService
{
    /** Collection of SolrInputDocuments. **/
    public Collection<SolrInputDocument> solrDocuments;

    @Inject
    private Logger logger;

    /**
     * Parses the CSF input with first line as a headers row into the collection of SolrInputDocuments.
     *
     * @param location the string url address from where to get the data to parse
     * @param headerToFiledMap the map between the headers in the data input stream and the correct field names
     * @param strategy represents the strategy for a CSV, mainly the separator character
     */
    public CSVFileService(String location, Map<String, String> headerToFiledMap, CSVStrategy strategy)
    {
        URL url;
        try {
            url = new URL(location);
        } catch (MalformedURLException ex) {
            throw new SolrException(SolrException.ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex);
        }

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()));

            String[] headers;

            String zeroLine = in.readLine();

            CSVParser headParser = new CSVParser(new StringReader(zeroLine), strategy);

            try {
                headers = headParser.getLine();
            } catch (IOException e) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
            }

            // get correct field names for velocity
            transformHeaders(headers, headerToFiledMap);
            parseLines(in, strategy, headers);

        } catch (NullPointerException ex) {
            this.logger.error("NullPointer: {}", ex.getMessage());
        } catch (IOException ex) {
            this.logger.error("IOException: {}", ex.getMessage());
        }

    }

    private void transformHeaders(String[] headers, Map<String, String> headerToFiledMap)
    {
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headerToFiledMap.get(headers[i]);
        }
    }

    private void parseLines(BufferedReader in, CSVStrategy strategy, String[] headers)
    {
        String line;

        try {
            while ((line = in.readLine()) != null) {
                String[] pieces;

                CSVParser parser = new CSVParser(new StringReader(line), strategy);

                try {
                    pieces = parser.getLine();
                } catch (IOException e) {
                    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
                }

                // Ignore the whole line if begins with tab symbol
                if (pieces.length != headers.length || "".equals(pieces[0])) {
                    continue;
                }

                SolrInputDocument crtTerm = new SolrInputDocument();
                int counter = 0;
                for (String term : pieces) {
                    if (!"".equals(term)) {
                        crtTerm.addField(headers[counter], term);
                    }
                    counter++;
                }

                this.solrDocuments.add(crtTerm);
            }
        } catch (IOException e) {
            this.logger.error(" IOException: {}", e.getMessage());
        }
    }

}
