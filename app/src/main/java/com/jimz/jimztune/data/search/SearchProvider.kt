package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType

class UnsupportedTypeException(message: String) : Exception(message)

interface SearchProvider {
    val name: String
    suspend fun search(query: String, type: SearchType): List<SearchItem>
}
