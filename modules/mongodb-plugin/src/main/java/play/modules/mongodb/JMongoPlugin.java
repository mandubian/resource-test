/*
 * Copyright 2012 Stephane Godbillon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.modules.mongodb;

import com.mongodb.Mongo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import play.Play;
import play.api.PlayException;
import scala.Option;

public class JMongoPlugin {
	/** @return the current instance of the plugin (from a play.Application - Scala's play.api.Application equivalent for Java). */
	public static MongoPlugin current() {
		return MongoPlugin.current(Play.application());
	}

	/** @return the current com.mongodb.Mongo connection. */
	public static Mongo connection() {
		return current().connection().underlying();
	}

	/** @return the current com.mongodb.DB. */
	public static DB db() {
		return current().db().underlying();
	}

	/** @return the named collection from current DB. */
	public static DBCollection collection(String name) {
		return current().collection(name).underlying();
	}
}