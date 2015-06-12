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

package org.phenotips.data.internal;

import org.phenotips.Constants;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.store.XWikiHibernateBaseStore.HibernateCallback;
import com.xpn.xwiki.store.XWikiHibernateStore;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;
import com.xpn.xwiki.store.migration.hibernate.AbstractHibernateDataMigration;

/**
 * Migration for PhenoTips issue #1280: Automatically migrate existing {@code genes} (with {@code genes_comments}),
 * {@code rejectedGenes} (with {@code rejectedGenes_comments}) and {@code solved__gene_id} values to the {@code genes}
 * (with {@code genes_comments}) objects of {@code InvestigationClass} with new additional property of
 * {@code classification} with "candidate", "rejected" and "solved" values respectively. Searches for all documents
 * containing values for the: 1.- {@code genes} (with {@code genes_comments}) property from {@code InvestigationClass}
 * and for each such document set the "candidate" value of {@code classification} of {@code InvestigationClass}. 2.-
 * {@code rejectedGenes} (with {@code rejectedGenes_comments}) property from {@code RejectedGenesClass} and for each
 * such document migrate property value to the {@code genes} (with {@code genes_comments}) object with "rejected" value
 * of {@code classification} of {@code InvestigationClass}. 3.- {@code solved__gene_id} property from
 * {@code PatientClass} and for each such document migrate property value to the {@code genes} (with
 * {@code genes_comments}) object with "solved" value of {@code classification} of {@code InvestigationClass}. The old
 * {@code rejectedGenes}, {@code rejectedGenes_comments}, {@code solved__gene_id} properties and
 * {@code RejectedGenesClass} are removed.
 *
 * @version $Id$
 * @since 1.2M1
 */
@Component
@Named("R70190PhenoTips#1280")
@Singleton
public class R70190PhenoTips1280DataMigration extends AbstractHibernateDataMigration implements
HibernateCallback<Object>
{
    private static final String GENE_NAME = "gene";

    private static final String COMMENTS_NAME = "comments";

    private static final String SOLVED_NAME = "solved__gene_id";

    private static final String CLASSIFICATION_NAME = "classification";

    /** Resolves unprefixed document names to the current wiki. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /** Serializes the class name without the wiki prefix, to be used in the database query. */
    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    @Override
    public String getDescription()
    {
        return "Migrate all existing gene values to the InvestigationClass objects";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(50291);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        getStore().executeWrite(getXWikiContext(), this);
    }

    @Override
    public Object doInHibernate(Session session) throws HibernateException, XWikiException
    {
        XWikiContext context = getXWikiContext();
        XWiki xwiki = context.getWiki();
        DocumentReference patientClassReference =
            new DocumentReference(context.getDatabase(), Constants.CODE_SPACE, "PatientClass");
        DocumentReference investigationClassReference =
            new DocumentReference(context.getDatabase(), Constants.CODE_SPACE, "InvestigationClass");
        DocumentReference rejectedGenesClassReference =
            new DocumentReference(context.getDatabase(), Constants.CODE_SPACE, "RejectedGenesClass");

        Query q =
            session.createQuery("select distinct o.name from BaseObject o, StringProperty p where o.className = '"
                + this.serializer.serialize(patientClassReference) + "' and p.id.id = o.id and p.id.name = '"
                + SOLVED_NAME + "' and p.value <> ''");
        @SuppressWarnings("unchecked")
        List<String> documents = q.list();
        for (String docName : documents) {
            XWikiDocument doc = xwiki.getDocument(this.resolver.resolve(docName), context);
            BaseObject patient = doc.getXObject(patientClassReference);
            StringProperty oldTarget = (StringProperty) patient.get(SOLVED_NAME);
            patient.removeField(SOLVED_NAME);
            BaseObject gene = doc.newXObject(investigationClassReference, context);
            gene.set(GENE_NAME, oldTarget.getValue(), context);
            gene.set(CLASSIFICATION_NAME, "solved", context);
            doc.setComment("Migrate solved gene values to the InvestigationClass objects");
            doc.setMinorEdit(true);
            try {
                // There's a bug in XWiki which prevents saving an object in the same session that it was loaded,
                // so we must clear the session cache first.
                session.clear();
                ((XWikiHibernateStore) getStore()).saveXWikiDoc(doc, context, false);
                session.flush();
            } catch (DataMigrationException e) {
            }
        }

        q = session.createQuery("select distinct o.name from BaseObject o where o.className = '"
            + this.serializer.serialize(investigationClassReference) + "'");
        @SuppressWarnings("unchecked")
        List<String> docs = q.list();
        for (String docName : docs) {
            XWikiDocument doc = xwiki.getDocument(this.resolver.resolve(docName), context);
            BaseObject gene = doc.getXObject(investigationClassReference);
            gene.set(CLASSIFICATION_NAME, "candidate", context);
            doc.setComment("Adding 'candidate' classification to existing "
                + "candidate gene values in the InvestigationClass objects");
            doc.setMinorEdit(true);
            try {
                session.clear();
                ((XWikiHibernateStore) getStore()).saveXWikiDoc(doc, context, false);
                session.flush();
            } catch (DataMigrationException e) {
            }
        }

        q = session.createQuery("select distinct o.name from BaseObject o where o.className = '"
            + this.serializer.serialize(rejectedGenesClassReference) + "'");
        @SuppressWarnings("unchecked")
        List<String> docums = q.list();
        for (String docName : docums) {
            XWikiDocument doc = xwiki.getDocument(this.resolver.resolve(docName), context);
            BaseObject gene = doc.getXObject(rejectedGenesClassReference);
            StringProperty oldGeneName = (StringProperty) gene.get(GENE_NAME);
            StringProperty oldGeneComments = (StringProperty) gene.get(COMMENTS_NAME);
            BaseObject newgene = doc.newXObject(investigationClassReference, context);
            newgene.set(GENE_NAME, oldGeneName.getValue(), context);
            newgene.set(COMMENTS_NAME, oldGeneComments.getValue(), context);
            newgene.set(CLASSIFICATION_NAME, "rejected", context);
            doc.setComment("Migrating rejected genes to the InvestigationClass objects");
            doc.setMinorEdit(true);
            session.delete(docName);
            try {
                session.clear();
                ((XWikiHibernateStore) getStore()).saveXWikiDoc(doc, context, false);
                session.flush();
            } catch (DataMigrationException e) {
            }
        }

        return null;
    }
}
