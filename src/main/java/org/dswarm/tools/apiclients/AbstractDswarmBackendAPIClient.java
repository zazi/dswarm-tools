/**
 * Copyright © 2016 SLUB Dresden (<code@dswarm.org>)
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
package org.dswarm.tools.apiclients;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.client.rx.RxWebTarget;
import org.glassfish.jersey.client.rx.rxjava.RxObservableInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import org.dswarm.common.types.Tuple;
import org.dswarm.tools.DswarmToolsStatics;
import org.dswarm.tools.utils.DswarmToolUtils;

/**
 * @author tgaengler
 */
public abstract class AbstractDswarmBackendAPIClient extends AbstractAPIClient {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractDswarmBackendAPIClient.class);

	protected final String OBJECTS_IDENTIFIER = String.format("%s%ss", SLASH, objectName);
	private static final String FORMAT_IDENTIFIER = "format";
	private static final String SHORT_FORMAT_IDENTIFIER = "short";

	public AbstractDswarmBackendAPIClient(final String dswarmBackendAPIBaseURI, final String objectName) {

		super(dswarmBackendAPIBaseURI, objectName);
	}

	public Observable<Tuple<String, String>> fetchObjects() {

		// 1. retrieve all objects (in short form)
		return retrieveAllObjectIds()
				// 2. for each object: retrieve complete object
				.flatMap(this::retrieveObject);
	}

	public Observable<Tuple<String, String>> importObjects(final Observable<Tuple<String, String>> objectDescriptionTupleObservable) {

		return objectDescriptionTupleObservable.flatMap(this::importObject);
	}

	private Observable<String> retrieveAllObjectIds() {

		final RxWebTarget<RxObservableInvoker> rxWebTarget = rxWebTarget(OBJECTS_IDENTIFIER);

		final RxObservableInvoker rx = rxWebTarget.queryParam(FORMAT_IDENTIFIER, SHORT_FORMAT_IDENTIFIER)
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.rx();

		return rx.get(String.class)
				.observeOn(exportScheduler)
				.map(objectDescriptionsJSON -> {

					final String errorMessage = String.format("something went wrong, while trying to retrieve short descriptions of all %ss", objectName);

					return DswarmToolUtils.deserializeAsArrayNode(objectDescriptionsJSON, errorMessage);
				})
				.flatMap(objectDescriptionsJSON -> Observable.from(objectDescriptionsJSON)
						.map(objectDescriptionJSON -> objectDescriptionJSON.get(DswarmToolsStatics.UUID_IDENTIFIER).asText()));
	}

	public Observable<Tuple<String, String>> retrieveObject(final String objectIdentifier) {

		LOG.debug("trying to retrieve full {} description for {} '{}'", objectName, objectName, objectIdentifier);

		final String requestURI = String.format("%s%s%s", OBJECTS_IDENTIFIER, SLASH, objectIdentifier);

		final RxWebTarget<RxObservableInvoker> rxWebTarget = rxWebTarget(requestURI);

		final RxObservableInvoker rx = rxWebTarget.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.rx();

		return rx.get(String.class)
				.observeOn(exportScheduler)
				.map(objectDescriptionJSONString -> {

					LOG.debug("retrieved full {} description for {} '{}'", objectName, objectName, objectIdentifier);

					return getObjectJSON(objectIdentifier, objectDescriptionJSONString);
				})
				.map(objectDescriptionJSON -> serializeObjectJSON(objectIdentifier, objectDescriptionJSON));
	}

	protected Observable<Tuple<String, String>> importObject(final Tuple<String, String> objectDescriptionTuple) {

		final String objectIdentifier = objectDescriptionTuple.v1();
		final String objectDescriptionJSONString = objectDescriptionTuple.v2();

		LOG.debug("trying to import full {} description of {} '{}'", objectName, objectName, objectIdentifier);

		final RxWebTarget<RxObservableInvoker> rxWebTarget = rxWebTarget(getObjectsImportEndpoint());

		final RxObservableInvoker rx = rxWebTarget.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.rx();

		return rx.post(Entity.entity(objectDescriptionJSONString, MediaType.APPLICATION_JSON), String.class)
				.observeOn(importScheduler)
				.map(responseObjectDescriptionJSONString -> {

					LOG.debug("imported full {} description for {} '{}'", objectName, objectName, objectIdentifier);

					return getObjectJSON(objectIdentifier, responseObjectDescriptionJSONString);
				})
				.map(objectDescriptionJSON -> serializeObjectJSON(objectIdentifier, objectDescriptionJSON));
	}

	protected String getObjectsImportEndpoint() {

		return OBJECTS_IDENTIFIER;
	}
}