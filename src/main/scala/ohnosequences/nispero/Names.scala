package ohnosequences.nispero

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._


object Names {

  val PRODUCT_PREFIX = "nispero"


  object Tables {
    val WORKERS_STATE_TABLE_PREFIX = PRODUCT_PREFIX + "WorkersState"
    val UNIQUE_ID_TABLE = PRODUCT_PREFIX + "UniqueId"

    val UNIQUE_HASH_KEY = new AttributeDefinition("id", S)
    val UNIQUE_RANGE_KEY = new AttributeDefinition("range", N)

    val WORKERS_STATE_HASH_KEY = new AttributeDefinition("id", N)
    // val WORKERS_STATE_HASH_KEY_VALUE = NumericValue(1)
    val WORKERS_STATE_RANGE_KEY = new AttributeDefinition("timestamp", N)
  }

}
