# Elasticsearch Vector Plugin

This Plugin allows you to score Elasticsearch documents based on embedding-vectors, using dot-product.

**Hint:** The dot-product ("euclidean distance") between two normalized vectors corresponds to their "cosine distance".
I.e. if you want to calculate the cosine similarity you have to normalize your vectors first (L2 or euclidean norm).
We recommend to store only the normalized vector together with its normalization factor in the index, because the 
original vector can be calculated from both at any time.


## General
This plugin is based on [this fast vector scoring plugin](https://github.com/lior-k/fast-elasticsearch-vector-scoring) 
which was inspired from [this vector scoring plugin](https://github.com/MLnick/elasticsearch-vector-scoring) and 
[this discussion](https://discuss.elastic.co/t/vector-scoring/85227/6) to achieve 10 times faster processing over the original.


## Elasticsearch version
The plugin is currently designed for Elasticsearch 7.2.0.
Since Elasticsearch 7.0.0 scores produced by a script_score function must be non-negative. To meet this requirement, 
all negative scores are truncated to zero.


## Maven configuration
* Clone the project
* `mvn package` to compile the plugin as a zip file
* In Elasticsearch run `elasticsearch-plugin install file:/PATH_TO_ZIP` to install plugin


## Usage

### Documents

Each document should have a field of type `vector` containing the vector. For example:
```
{
    "image_vector": [
        0.524272620677948,
        -0.07923126220703125,
        0.6668860912322998,
        0.17430995404720306,
        0.08961634337902069,
        -0.04255223646759987,
        -0.28892049193382263,
        -0.09446816891431808,
        0.3486887514591217,
        0.14111995697021484
    ]
}
```

Use this field mapping:
```
{
    "properties": {
        "image_vector": { 
            "type": "vector"
        }
    }
}
```

### Querying
For querying the most similar top-10 documents use this POST message on your ES index:

```
{
    "query": {
        "function_score": {
            "boost_mode": "replace",
            "script_score": {
                "script": {
                    "source": "vector_score",
                    "lang": "vector_score",
                    "params": {
                        "field": "image_vector",
                        "vector": [
                            -0.24658453464508057,
                            0.4480297565460205,
                            0.41574031114578247,
                            -0.08298800140619278,
                            0.5172473192214966,
                            0.43442630767822266,
                            -0.2519787847995758,
                            -0.059472210705280304,
                            -0.15016762912273407,
                            -0.11352039873600006
                        ]
                    }
                }
            }
        }
    },
    "size": 10
}
```

The example above shows a vector of 10 dimensions.
* Parameters:
   - `field`: The field containing the vector.
   - `vector`: The vector to compare to.
 
## Performance

Since we are using doc values that are actually 
[column-stride fields](https://www.elastic.co/de/blog/sparse-versus-dense-document-values-with-apache-lucene)
retrieval is blazing fast. Query performance scales linearly with `number_of_shards`.