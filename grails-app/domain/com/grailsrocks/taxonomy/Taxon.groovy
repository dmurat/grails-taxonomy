package com.grailsrocks.taxonomy

class Taxon {
    String name
    Date dateCreated
    Date lastUpdated

    static belongsTo = [parent: Taxon, scope: Taxonomy]
    static hasMany = [children: Taxon, taxonLinks: TaxonLink]

    static constraints = {
        parent(nullable:true)
    }

    static mapping = {
        cache true
        name index:'taxon_name_idx'
    }
}
