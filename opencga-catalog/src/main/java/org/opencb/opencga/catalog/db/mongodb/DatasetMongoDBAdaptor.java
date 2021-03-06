/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.db.mongodb;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.commons.datastore.mongodb.MongoDBCollection;
import org.opencb.opencga.catalog.db.api.DBIterator;
import org.opencb.opencga.catalog.db.api.DatasetDBAdaptor;
import org.opencb.opencga.catalog.db.mongodb.converters.DatasetConverter;
import org.opencb.opencga.catalog.db.mongodb.iterators.MongoDBIterator;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.core.models.Dataset;
import org.opencb.opencga.core.models.Status;
import org.opencb.opencga.core.models.acls.permissions.StudyAclEntry;
import org.opencb.opencga.core.common.TimeUtils;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static org.opencb.opencga.catalog.db.mongodb.MongoDBUtils.*;

/**
 * Created by pfurio on 04/05/16.
 */
public class DatasetMongoDBAdaptor extends MongoDBAdaptor implements DatasetDBAdaptor {

    private final MongoDBCollection datasetCollection;
    private DatasetConverter datasetConverter;

    /***
     * CatalogMongoDatasetDBAdaptor constructor.
     *
     * @param datasetCollection MongoDB connection to the dataset collection.
     * @param dbAdaptorFactory Generic dbAdaptorFactory containing all the different collections.
     */
    public DatasetMongoDBAdaptor(MongoDBCollection datasetCollection, MongoDBAdaptorFactory dbAdaptorFactory) {
        super(LoggerFactory.getLogger(FileMongoDBAdaptor.class));
        this.dbAdaptorFactory = dbAdaptorFactory;
        this.datasetCollection = datasetCollection;
        this.datasetConverter = new DatasetConverter();
    }

    /**
     *
     * @return MongoDB connection to the file collection.
     */
    public MongoDBCollection getDatasetCollection() {
        return datasetCollection;
    }

    @Override
    public QueryResult<Dataset> insert(Dataset dataset, long studyId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();
        dbAdaptorFactory.getCatalogStudyDBAdaptor().checkId(studyId);

        Query query = new Query(QueryParams.NAME.key(), dataset.getName()).append(QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Long> count = count(query);

        if (count.getResult().get(0) > 0) {
            throw new CatalogDBException("Dataset { name: \"" + dataset.getName() + "\" } already exists in the study { " + studyId
                    + " }.");
        }

        long newId = getNewId();
        dataset.setId(newId);

        Document datasetObject = datasetConverter.convertToStorageType(dataset);
        datasetCollection.insert(datasetObject, null);

        return endQuery("createDataset", startTime, get(dataset.getId(), options));
    }

    @Override
    public long getStudyIdByDatasetId(long datasetId) throws CatalogDBException {
        checkId(datasetId);
        Query query = new Query(QueryParams.ID.key(), datasetId);
        QueryOptions queryOptions = new QueryOptions(MongoDBCollection.INCLUDE, PRIVATE_STUDY_ID);
        Document dataset = (Document) nativeGet(query, queryOptions).first();

        return dataset.getLong(PRIVATE_STUDY_ID);
    }

    @Override
    public QueryResult<Dataset> get(long datasetId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

        Query query = new Query(QueryParams.ID.key(), datasetId).append(QueryParams.STATUS_NAME.key(), "!=" + Status.DELETED);
        return endQuery("Get dataset", startTime, get(query, options));
    }

    @Override
    public QueryResult<Dataset> get(Query query, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();
        if (!query.containsKey(QueryParams.STATUS_NAME.key())) {
            query.append(QueryParams.STATUS_NAME.key(), "!=" + Status.TRASHED + ";!=" + Status.DELETED);
        }
        Bson bson;
        try {
            bson = parseQuery(query, false);
        } catch (NumberFormatException e) {
            throw new CatalogDBException("Get dataset: Could not parse all the arguments from query - " + e.getMessage(), e.getCause());
        }
        QueryOptions qOptions;
        if (options != null) {
            qOptions = options;
        } else {
            qOptions = new QueryOptions();
        }
        qOptions = filterOptions(qOptions, FILTER_ROUTE_DATASETS);

        QueryResult<Dataset> datasetQueryResult = datasetCollection.find(bson, datasetConverter, qOptions);
        logger.debug("Dataset get: query : {}, project: {}, dbTime: {}", bson, qOptions == null ? "" : qOptions.toJson(),
                datasetQueryResult.getDbTime());
        return endQuery("Get Dataset", startTime, datasetQueryResult);
    }

    @Override
    public QueryResult<Dataset> get(Query query, QueryOptions options, String user) throws CatalogDBException {
        throw new NotImplementedException("Get not implemented for dataset");
    }

    @Override
    public QueryResult nativeGet(Query query, QueryOptions options) throws CatalogDBException {
        Bson bson;
        if (!query.containsKey(QueryParams.STATUS_NAME.key())) {
            query.append(QueryParams.STATUS_NAME.key(), "!=" + Status.TRASHED + ";!=" + Status.DELETED);
        }
        try {
            bson = parseQuery(query, false);
        } catch (NumberFormatException e) {
            throw new CatalogDBException("Get dataset: Could not parse all the arguments from query - " + e.getMessage(), e.getCause());
        }
        QueryOptions qOptions;
        if (options != null) {
            qOptions = options;
        } else {
            qOptions = new QueryOptions();
        }
        qOptions = filterOptions(qOptions, FILTER_ROUTE_DATASETS);

        return datasetCollection.find(bson, qOptions);
    }

    @Override
    public QueryResult<Dataset> update(long id, ObjectMap parameters) throws CatalogDBException {
        long startTime = startQuery();
        update(new Query(QueryParams.ID.key(), id), parameters);
        return endQuery("Update dataset", startTime, get(id, new QueryOptions()));
    }

    @Override
    public QueryResult<Long> update(Query query, ObjectMap parameters) throws CatalogDBException {
        long startTime = startQuery();
        Map<String, Object> datasetParams = new HashMap<>();

        String[] acceptedParams = {QueryParams.DESCRIPTION.key(), QueryParams.NAME.key(), QueryParams.CREATION_DATE.key()};
        filterStringParams(parameters, datasetParams, acceptedParams);

        String[] acceptedLongListParams = {QueryParams.FILES.key()};
        filterLongParams(parameters, datasetParams, acceptedLongListParams);
        if (parameters.containsKey(QueryParams.FILES.key())) {
            for (Long fileId : parameters.getAsLongList(QueryParams.FILES.key())) {
                if (!dbAdaptorFactory.getCatalogFileDBAdaptor().exists(fileId)) {
                    throw CatalogDBException.idNotFound("File", fileId);
                }
            }
        }

        String[] acceptedMapParams = {QueryParams.ATTRIBUTES.key()};
        filterMapParams(parameters, datasetParams, acceptedMapParams);

        if (parameters.containsKey(QueryParams.STATUS_NAME.key())) {
            datasetParams.put(QueryParams.STATUS_NAME.key(), parameters.get(QueryParams.STATUS_NAME.key()));
            datasetParams.put(QueryParams.STATUS_DATE.key(), TimeUtils.getTime());
        }

        if (!datasetParams.isEmpty()) {
            QueryResult<UpdateResult> update = datasetCollection.update(parseQuery(query, false), new Document("$set", datasetParams),
                    null);
            return endQuery("Update cohort", startTime, Arrays.asList(update.getNumTotalResults()));
        }

        return endQuery("Update cohort", startTime, new QueryResult<>());
    }

    @Override
    public void delete(long id) throws CatalogDBException {
        Query query = new Query(QueryParams.ID.key(), id);
        delete(query);
    }

    @Override
    public void delete(Query query) throws CatalogDBException {
        QueryResult<DeleteResult> remove = datasetCollection.remove(parseQuery(query, false), null);

        if (remove.first().getDeletedCount() == 0) {
            throw CatalogDBException.deleteError("Dataset");
        }
    }

    @Override
    public QueryResult<Dataset> delete(long id, QueryOptions queryOptions) throws CatalogDBException    {
        long startTime = startQuery();

        checkId(id);
        // Check the dataset is active
        Query query = new Query(QueryParams.ID.key(), id).append(QueryParams.STATUS_NAME.key(), Status.READY);
        if (count(query).first() == 0) {
            query.put(QueryParams.STATUS_NAME.key(), Status.TRASHED + "," + Status.DELETED);
            QueryOptions options = new QueryOptions(MongoDBCollection.INCLUDE, QueryParams.STATUS_NAME.key());
            Dataset dataset = get(query, options).first();
            throw new CatalogDBException("The dataset {" + id + "} was already " + dataset.getStatus().getName());
        }

        // Change the status of the dataset to deleted
        setStatus(id, Status.TRASHED);

        query = new Query(QueryParams.ID.key(), id).append(QueryParams.STATUS_NAME.key(), Status.TRASHED);

        return endQuery("Delete dataset", startTime, get(query, null));
    }

    @Override
    public QueryResult<Long> delete(Query query, QueryOptions queryOptions) throws CatalogDBException {
        long startTime = startQuery();
        query.append(QueryParams.STATUS_NAME.key(), Status.READY);
        QueryResult<Dataset> datasetQueryResult = get(query, new QueryOptions(MongoDBCollection.INCLUDE, QueryParams.ID.key()));
        for (Dataset dataset : datasetQueryResult.getResult()) {
            delete(dataset.getId(), queryOptions);
        }
        return endQuery("Delete dataset", startTime, Collections.singletonList(datasetQueryResult.getNumTotalResults()));
    }

    QueryResult<Dataset> setStatus(long datasetId, String status) throws CatalogDBException {
        return update(datasetId, new ObjectMap(QueryParams.STATUS_NAME.key(), status));
    }

    QueryResult<Long> setStatus(Query query, String status) throws CatalogDBException {
        return update(query, new ObjectMap(QueryParams.STATUS_NAME.key(), status));
    }

    @Override
    public QueryResult<Dataset> remove(long id, QueryOptions queryOptions) throws CatalogDBException {
        return null;
    }

    @Override
    public QueryResult<Long> remove(Query query, QueryOptions queryOptions) throws CatalogDBException {
        return null;
    }

    @Override
    public QueryResult<Long> restore(Query query, QueryOptions queryOptions) throws CatalogDBException {
        long startTime = startQuery();
        query.put(QueryParams.STATUS_NAME.key(), Status.TRASHED);
        return endQuery("Restore datasets", startTime, setStatus(query, Status.READY));
    }

    @Override
    public QueryResult<Dataset> restore(long id, QueryOptions queryOptions) throws CatalogDBException {
        long startTime = startQuery();

        checkId(id);
        // Check if the cohort is active
        Query query = new Query(QueryParams.ID.key(), id)
                .append(QueryParams.STATUS_NAME.key(), Status.TRASHED);
        if (count(query).first() == 0) {
            throw new CatalogDBException("The dataset {" + id + "} is not deleted");
        }

        // Change the status of the cohort to deleted
        setStatus(id, Status.READY);
        query = new Query(QueryParams.ID.key(), id);

        return endQuery("Restore dataset", startTime, get(query, null));
    }

    @Override
    public QueryResult<Long> insertFilesIntoDatasets(Query query, List<Long> fileIds) throws CatalogDBException {
        long startTime = startQuery();
        Bson bsonQuery = parseQuery(query, false);
        Bson update = new Document("$push", new Document(QueryParams.FILES.key(), new Document("$each", fileIds)));
        QueryOptions multi = new QueryOptions(MongoDBCollection.MULTI, true);
        QueryResult<UpdateResult> updateQueryResult = datasetCollection.update(bsonQuery, update, multi);
        return endQuery("Insert files into datasets", startTime, Collections.singletonList(updateQueryResult.first().getModifiedCount()));
    }

    @Override
    public QueryResult<Long> extractFilesFromDatasets(Query query, List<Long> fileIds) throws CatalogDBException {
        long startTime = startQuery();
        Bson bsonQuery = parseQuery(query, false);
        Bson update = new Document("$pull", new Document(QueryParams.FILES.key(), new Document("$in", fileIds)));
        QueryOptions multi = new QueryOptions(MongoDBCollection.MULTI, true);
        QueryResult<UpdateResult> updateQueryResult = datasetCollection.update(bsonQuery, update, multi);
        return endQuery("Extract files from datasets", startTime, Collections.singletonList(updateQueryResult.first().getModifiedCount()));
    }

    @Override
    public QueryResult<Long> count(Query query) throws CatalogDBException {
        Bson bson = parseQuery(query, false);
        return datasetCollection.count(bson);
    }

    @Override
    public QueryResult<Long> count(Query query, String user, StudyAclEntry.StudyPermissions studyPermission) throws CatalogDBException {
        throw new NotImplementedException("Count not implemented for datasets");
    }

    @Override
    public QueryResult distinct(Query query, String field) throws CatalogDBException {
        Bson bsonDocument = parseQuery(query, false);
        return datasetCollection.distinct(field, bsonDocument);
    }

    @Override
    public QueryResult stats(Query query) {
        return null;
    }

    @Override
    public DBIterator<Dataset> iterator(Query query, QueryOptions options) throws CatalogDBException {
        Bson bson = parseQuery(query, false);
        MongoCursor<Document> iterator = datasetCollection.nativeQuery().find(bson, options).iterator();
        return new MongoDBIterator<>(iterator, datasetConverter);
    }

    @Override
    public DBIterator nativeIterator(Query query, QueryOptions options) throws CatalogDBException {
        Bson bson = parseQuery(query, false);
        MongoCursor<Document> iterator = datasetCollection.nativeQuery().find(bson, options).iterator();
        return new MongoDBIterator<>(iterator);
    }

    @Override
    public DBIterator<Dataset> iterator(Query query, QueryOptions options, String user)
            throws CatalogDBException, CatalogAuthorizationException {
        return null;
    }

    @Override
    public DBIterator nativeIterator(Query query, QueryOptions options, String user)
            throws CatalogDBException, CatalogAuthorizationException {
        return null;
    }

    @Override
    public QueryResult rank(Query query, String field, int numResults, boolean asc) throws CatalogDBException {
        Bson bsonQuery = parseQuery(query, false);
        return rank(datasetCollection, bsonQuery, field, "name", numResults, asc);
    }

    @Override
    public QueryResult groupBy(Query query, String field, QueryOptions options) throws CatalogDBException {
        Bson bsonQuery = parseQuery(query, false);
        return groupBy(datasetCollection, bsonQuery, field, "name", options);
    }

    @Override
    public QueryResult groupBy(Query query, List<String> fields, QueryOptions options) throws CatalogDBException {
        Bson bsonQuery = parseQuery(query, false);
        return groupBy(datasetCollection, bsonQuery, fields, "name", options);
    }

    @Override
    public void forEach(Query query, Consumer<? super Object> action, QueryOptions options) throws CatalogDBException {
        Objects.requireNonNull(action);
        try (DBIterator<Dataset> catalogDBIterator = iterator(query, options)) {
            while (catalogDBIterator.hasNext()) {
                action.accept(catalogDBIterator.next());
            }
        }
    }

    private Bson parseQuery(Query query, boolean isolated) throws CatalogDBException {
        List<Bson> andBsonList = new ArrayList<>();

        if (isolated) {
            andBsonList.add(new Document("$isolated", 1));
        }

        for (Map.Entry<String, Object> entry : query.entrySet()) {
            String key = entry.getKey().split("\\.")[0];
            QueryParams queryParam = QueryParams.getParam(entry.getKey()) != null
                    ? QueryParams.getParam(entry.getKey())
                    : QueryParams.getParam(key);
            try {
                switch (queryParam) {
                    case ID:
                        addOrQuery(PRIVATE_ID, queryParam.key(), query, queryParam.type(), andBsonList);
                        break;
                    case STUDY_ID:
                        addOrQuery(PRIVATE_STUDY_ID, queryParam.key(), query, queryParam.type(), andBsonList);
                        break;
                    case ATTRIBUTES:
                        addAutoOrQuery(entry.getKey(), entry.getKey(), query, queryParam.type(), andBsonList);
                        break;
                    case BATTRIBUTES:
                        String mongoKey = entry.getKey().replace(QueryParams.BATTRIBUTES.key(), QueryParams.ATTRIBUTES.key());
                        addAutoOrQuery(mongoKey, entry.getKey(), query, queryParam.type(), andBsonList);
                        break;
                    case NATTRIBUTES:
                        mongoKey = entry.getKey().replace(QueryParams.NATTRIBUTES.key(), QueryParams.ATTRIBUTES.key());
                        addAutoOrQuery(mongoKey, entry.getKey(), query, queryParam.type(), andBsonList);
                        break;
                    default:
                        addAutoOrQuery(queryParam.key(), queryParam.key(), query, queryParam.type(), andBsonList);
                        break;
                }
            } catch (Exception e) {
                logger.error("Error with " + entry.getKey() + " " + entry.getValue());
                throw new CatalogDBException(e);
            }
        }

        if (andBsonList.size() > 0) {
            return Filters.and(andBsonList);
        } else {
            return new Document();
        }
    }

}
