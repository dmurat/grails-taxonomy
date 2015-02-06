package com.grailsrocks.taxonomy

class TaxonomyService {
    static transactional = true

    static DELIMITER = ':::'

    static GLOBAL_TAXONOMY_NAME = '_global'

    Taxonomy globalTaxonomy

    void init() {
        globalTaxonomy = Taxonomy.findByName(GLOBAL_TAXONOMY_NAME)
        if (!globalTaxonomy) {
            globalTaxonomy = new Taxonomy(name:TaxonomyService.GLOBAL_TAXONOMY_NAME).save()
            assert globalTaxonomy
        }
    }

    Taxonomy resolveTaxonomy(taxonomyNameOrInstance, boolean create = false) {
        def taxonomy = taxonomyNameOrInstance

        if (taxonomy) {
            if (!(taxonomy instanceof Taxonomy)) {
                taxonomy = Taxonomy.findByName(taxonomy.toString())
                if (!taxonomy) {
                    if (create) {
                        taxonomy = new Taxonomy(name:taxonomyNameOrInstance.toString())
                        assert taxonomy.save()
                    }
                    else {
                        throw new IllegalArgumentException("No taxonomy with name [${taxonomyNameOrInstance}] found")
                    }
                }
            }
        }
        else {
            taxonomy = globalTaxonomy
        }

        return taxonomy
    }

    /**
     * Find all OBJECT INSTANCES with the specified taxonomic path or node
     * @params params Can contain optional "taxonomy" string/Taxonomy instance to scope the find to a particular graph
     * If not specified, it will not filter by taxonomy graph
     */
    def findObjectsByTaxon(Class domClass, def nodeOrPath, def params = null) {
        // @todo work out correct base class to use
        def taxonomy = resolveTaxonomy(params?.taxonomy)
        def node = resolveTaxon(nodeOrPath, taxonomy)

        if (log.debugEnabled) {
            log.debug( "findObjectsByTaxon $domClass, $nodeOrPath, $params")
        }

        if (node) {
            return getObjectsForTaxonIds(domClass, [node.id], params)
        }
        else {
            if (log.warnEnabled) {
                log.warn( "findObjectsByTaxon returning null because there is no Taxon at [$nodeOrPath] in taxonomy [${taxonomy?.name}]")
            }

            return Collections.EMPTY_LIST
        }
    }

    protected getObjectsForTaxonIds(objClass, taxonList, params) {
        if (taxonList) {
            def ids = TaxonLink.withCriteria {
                projections {
                    property('objectId')
                }

                taxon {
                    if (taxonList.size() == 1) {
                        eq('id', taxonList[0])
                    }
                    else {
                        inList('id', taxonList)
                    }
                }

                eq('className', objClass.name)
            }

            // Bug in Grails 1.2M2, inList dies if id list is empty
            if (log.debugEnabled) {
                log.debug( "getObjectIdsForTaxons found object ids $ids")
            }

            if (ids) {
                return objClass.findAllByIdInList(ids, params)
            }
        }

        return Collections.EMPTY_LIST
    }

    /**
     * Find all OBJECT INSTANCES that have the given taxon node/path or any of its subtypes
     */
    def findObjectsByFamily(def objClass, def nodeOrPath, def params = null) {
        def taxonomy = resolveTaxonomy(params?.taxonomy)
        def parent = resolveTaxon(nodeOrPath, taxonomy)

        def familyTaxonIds = findTaxonIdsDescendedFrom(parent, params) + parent.id
        if (familyTaxonIds) {
            return getObjectsForTaxonIds(objClass, familyTaxonIds, params)
        }
        else {
            if (log.warnEnabled) {
                log.warn( "findObjectsByFamily returning null because there are no Taxons in the family [$nodeOrPath] in taxonomy [${taxo?.name}]")
            }

            return Collections.EMPTY_LIST
        }
    }

    /**
     * Find all Taxon that are children of the given taxon. Params can contain 'taxonomy' string/Taxonomy object for scoping
     */
    def findTaxonsByParent(parent, Map params = null) {
        def taxonomy = resolveTaxonomy(params?.taxonomy)
        parent = resolveTaxon(parent, taxonomy)

        if (parent) {
            Taxon.findAllByParentAndScope(parent, taxonomy, params)
        }
        else {
            Taxon.findAllByParentIsNullAndScope(taxonomy, params)
        }
    }

    /**
     * Find all Taxon that are children of the given LIST of Taxon objects.
     */
    def findTaxonsByParentTaxons(parentTaxonList, Map params = null) {
        if (log.debugEnabled) {
            log.debug "findTaxonsByParentTaxons $parentTaxonList, $params"
        }

        resolveTaxonomy(params?.taxonomy)
        def parentIds = parentTaxonList*.id
        if (parentIds) {
            return Taxon.withCriteria {
                parent {
                    inList('id', parentIds)
                }
                def criteriaParamsClosure = criteriaParams.clone()
                criteriaParamsClosure.delegate = delegate
                criteriaParamsClosure.call(params)
            }
        }

        return Collections.EMPTY_LIST
    }

    /**
     * Find all Taxon that are children of the given LIST of Taxon objects.
     */
    def findTaxonIdsByParentTaxonIds(parentTaxonIdList, Map params = null) {
        if (log.debugEnabled) {
            log.debug "findTaxonsByParentTaxonIds $parentTaxonIdList, $params"
        }

        resolveTaxonomy(params?.taxonomy)
        if (parentTaxonIdList) {
            return Taxon.withCriteria {
                projections {
                    property('id')
                }
                parent {
                    inList('id', parentTaxonIdList)
                }
                def criteriaParamsClosure = criteriaParams.clone()
                criteriaParamsClosure.delegate = delegate
                criteriaParamsClosure.call(params)
            }
        }

        return Collections.EMPTY_LIST
    }

    protected recursivelyGatherTaxons(parentList, targetList, params = null) {
        if (log.debugEnabled) {
            log.debug "recursivelyGatherTaxons $parentList, $targetList"
        }

        def interim = findTaxonsByParentTaxons(parentList, params)
        if (interim) {
            targetList.addAll(interim)
            recursivelyGatherTaxons(interim, targetList)
        }

        return targetList
    }

    protected recursivelyGatherTaxonIds(parentIdList, targetList, params = null) {
        if (log.debugEnabled) {
            log.debug "recursivelyGatherTaxonIds $parentIdList, $targetList"
        }

        def interim = findTaxonIdsByParentTaxonIds(parentIdList, params)
        if (interim) {
            targetList.addAll(interim)
            recursivelyGatherTaxonIds(interim, targetList)
        }

        return targetList
    }

    /**
     * Find all Taxon that are children of the given taxon. Params can contain 'taxonomy' string/Taxonomy object for scoping
     */
    def findTaxonsDescendedFrom(parent, Map params = null) {
        if (log.debugEnabled) {
            log.debug "findTaxonsDescendedFrom $parent, $params"
        }

        def taxonomy = resolveTaxonomy(params?.taxonomy)
        parent = resolveTaxon(parent, taxonomy)

        if (parent) {
            return recursivelyGatherTaxons([parent], [], params)
        }
        else {
            return Taxon.findAllByScope(taxonomy, params)
        }
    }

    /**
     * Find all Taxon that are children of the given taxon. Params can contain 'taxonomy' string/Taxonomy object for scoping
     */
    def findTaxonIdsDescendedFrom(parent, Map params = null) {
        if (log.debugEnabled) {
            log.debug "findTaxonIdsDescendedFrom $parent, $params"
        }

        def taxonomy = resolveTaxonomy(params?.taxonomy)
        parent = resolveTaxon(parent, taxonomy)

        if (parent) {
            return recursivelyGatherTaxonIds([parent.id], [], params)
        }
        else {
            return Taxon.findAllByScope(taxonomy, params)*.id // @todo use criteria + projection
        }
    }

    /**
     * Convert and apply max, offset and sort/order params within a criteria
     */
    protected criteriaParams = { params ->
        if (params?.offset) {
            firstResult(params.offset.toString().toInteger())
        }

        if (params?.max) {
            maxResults(params.max.toString().toInteger())
        }

        if (params?.sort) {
            order(params.sort, params?.order ?: 'asc')
        }
    }

    /**
     * Find all Taxon that are children of the given taxon. Params can contain 'taxonomy' string/Taxonomy object for scoping
     */
    def findTaxonsByParentAndCriteria(parent, def params, Closure criteria) {
        def taxonomy = resolveTaxonomy(params?.taxonomy)
        parent = resolveTaxon(parent, taxonomy)

        if (log.debugEnabled) {
            log.debug "findTaxonsbyParentAndCriteria resolved parent to ${parent?.dump()} and taxonomy to ${taxonomy.dump()}"
        }

        def taxonList = Taxon.withCriteria {
            eq('scope', taxonomy)
            if (parent) {
                eq('parent', parent)
            }
            else {
                isNull('parent')
            }

            def criteriaParamsClosure = criteriaParams.clone()
            criteriaParamsClosure.delegate = delegate
            criteriaParamsClosure.call(params)

            criteria.delegate = delegate
            criteria.call()
        }

        if (log.debugEnabled) {
            log.debug "findTaxonsbyParentAndCriteria found: ${taxonList.dump()}"
        }

        return taxonList
    }

    /**
     * Take a Taxon or path List/string and taxonomy and find the Taxon instance representing it
     * @return Returns the Taxon indicated by the path, or null if the path cannot be fully resolved
     */
    Taxon resolveTaxon(nodeOrPath, taxonomy = null) {
        // Start searching assuming null parent for first element
        if (nodeOrPath instanceof Taxon) {
            return nodeOrPath
        }

        taxonomy = resolveTaxonomy(taxonomy, true) // Create taxonomies that are searched for

        if (log.debugEnabled) {
            log.debug( "resolveTaxon ${nodeOrPath?.dump()}, ${taxonomy.dump()}")
        }

        if (!(nodeOrPath instanceof List)) {
            nodeOrPath = nodeOrPath.toString().split(TaxonomyService.DELIMITER)
        }

        def previousTaxon
        def currentTaxon = null
        def notFound = null
        nodeOrPath.find { taxonToBeFound ->
            if (log.debugEnabled) {
                log.debug "resolveTaxon in loop - [$taxonToBeFound], previous is [$previousTaxon?.name]"
            }

            currentTaxon = Taxon.createCriteria().get {
                eq('name', taxonToBeFound)
                eq('scope', taxonomy)

                if (previousTaxon) {
                    eq('parent', previousTaxon)
                }
                else {
                    isNull('parent')
                }
            }

            previousTaxon = currentTaxon
            if (!currentTaxon) {
                notFound = true
                return true // Only break out if we don't find one, eg path not valid
            }

            return false
        }

        if (log.debugEnabled) {
            log.debug( "resolveTaxon resolved? ${!notFound} (${currentTaxon?.dump()})")
        }

        return notFound ? null : currentTaxon
    }

    /**
     * Find the TaxonLink object for the given taxon node or path, for the given object instance - if any
     */
    def findLink(Object obj, taxon, taxonomy = null) {
        if (taxon instanceof Taxon) {
            def criteria = TaxonLink.createCriteria()
            criteria.get  {
                eq('objectId', obj.id)
                eq('taxon', taxon)
                eq('className', obj.class.name)
            }
        }
        else {
            // @todo Here we need to check against a cache first to find the last Taxon in the chain

            def resolvedTaxon = resolveTaxon(taxon, taxonomy)
            if (resolvedTaxon) {
                findLink(obj, resolvedTaxon) // recurse once!
            }
            else {
                return null
            }
        }
    }

    /**
     * Remove the link to a taxon node (or path) for the given object instance
     */
    void removeLink(obj, taxonOrPath, taxonomy = null) {
        findLink(obj, taxonOrPath, taxonomy)?.delete()
    }

    /**
     * Remove all links for the given object instance
     */
    void removeAllLinks(obj) {
        findAllLinks(obj)*.delete()
    }

    /**
     * Test if there is a link to this taxon for given object instance
     */
    boolean hasLink(obj, taxonOrPath, taxonomy = null) {
        findLink(obj, taxonOrPath, taxonomy) ? true : false
    }

    /**
     * Find all the TaxonLink objects for the give taxonomy for the given object instance - if any
     */
    List findAllLinks(Object obj) {
        def criteria = TaxonLink.createCriteria()
        return criteria.list {
            eq('objectId', obj.id)
            eq('className', obj.class.name)
        }
    }

    TaxonLink saveNewLink(obj, Taxon node) {
        if (log.debugEnabled) {
            log.debug "saveNewLink $obj, $node"
        }

        def taxonLink = new TaxonLink(taxon:node, className:obj.class.name, objectId:obj.id)
        if (!taxonLink.save()) {
            if (log.errorEnabled) {
                log.error "Failed to save taxon link: ${taxonLink.errors}"
            }

            assert !taxonLink.errors
        }

        return taxonLink
    }

    /**
     * Force the specified path of taxons to exist in the specified taxonomy
     * If no taxonomy is specified, uses globalTaxonomy
     */
    Taxon createTaxonomyPath(path, taxonomy = null) {
        taxonomy = resolveTaxonomy(taxonomy, true)

        if (!(path instanceof List)) {
            path = path.toString().split(TaxonomyService.DELIMITER)
        }

        def previousTaxon
        def currentTaxon = null
        path.each { taxonName ->
            currentTaxon = Taxon.createCriteria().get {
                eq('name', taxonName)
                eq('scope', taxonomy)
                if (previousTaxon) {
                    eq('parent', previousTaxon)
                }
                else {
                    isNull('parent')
                }
            }

            if (!currentTaxon) {
                if (log.debugEnabled) {
                    log.debug "Creating new Taxon with name ${taxonName} in scope ${taxonomy?.dump()} with parent ${previousTaxon?.name}"
                }

                currentTaxon = new Taxon(name:taxonName, scope:taxonomy, parent:previousTaxon).save()
                assert currentTaxon
            }

            previousTaxon = currentTaxon
        }

        if (log.debugEnabled) {
            log.debug "Returning new Taxon with name ${currentTaxon}"
        }

        return currentTaxon
    }

    void dumpTaxonomy(taxonomy = null) {
        taxonomy = resolveTaxonomy(taxonomy)
        println "#"*40
        println "Taxon paths in taxonomy [${taxonomy.name}]"
        println "#"*40
        Taxon.listOrderByDateCreated().each { t->
            def path = new StringBuffer()
            path.insert(0, t.name)
            while (t.parent) {
                path.insert(0, "${t.parent.name} > ")
                t = t.parent
            }
            println path
        }
    }

    List resolveTaxonPath(Taxon taxonToResolvePath, taxonomy = null) {
        if (!taxonToResolvePath) {
            return []
        }

        taxonomy = resolveTaxonomy(taxonomy)

        if (log.debugEnabled) {
            log.debug( "resolveTaxonPath ${taxonToResolvePath.dump()}, ${taxonomy.dump()}")
        }

        List taxonNameReverseList = []
        taxonNameReverseList << taxonToResolvePath.name

        def parentTaxon = taxonToResolvePath.parent

        while(parentTaxon) {
            taxonNameReverseList << parentTaxon.name
            parentTaxon = parentTaxon.parent
        }

        return taxonNameReverseList.reverse()
    }
}
