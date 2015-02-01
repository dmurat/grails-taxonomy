package com.grailsrocks.taxonomy

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class Taxonomy {

    String name

    static mapping = {
        cache true
    }

    static constraints = {
        name(nullable: false, size: 1..200)
    }
}
