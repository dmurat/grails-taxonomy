
import com.grailsrocks.taxonomy.Taxon
import com.grailsrocks.taxonomy.TaxonomyService

class TaxonomyGrailsPlugin {
    // the plugin version
    def version = "1.2"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1.1 > *"
    // the other plugins this plugin depends on
    def dependsOn = [domainClass:'1.1.1 > *']
    def observe = ['domainClass']
    def loadAfter = ['hibernate']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/com/grailsrocks/taxonomy/test/Book.groovy"
    ]

    def author = "Marc Palmer"
    def authorEmail = "marc@grailsrocks.com"
    def title = "Taxonomy Plugin"
    def description = '''\\
Add hierarchical tags (taxonomies) to any domain classes.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/Taxonomy+Plugin"

    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
    }

    def doWithDynamicMethods = { ctx ->
        TaxonomyService taxonomyService = ctx.taxonomyService

        // Make sure global taxonomy is initialized
        taxonomyService.init()

        applyDynamicMethods(application)
    }

    static applyDynamicMethods(application) {
        TaxonomyService taxonomyService = application.mainContext.taxonomyService

        application.domainClasses*.clazz.each { domainClazz ->
            if (domainClazz.metaClass.hasProperty(domainClazz, 'taxonomy') && domainClazz.taxonomy) {
                // family can include "taxonomy" arg, string/Taxonomy instance
                domainClazz.metaClass.'static'.findByTaxonomyFamily = { nodeOrPath, Map params = null ->
                    if (!params) {
                        params = [max:1]
                    }
                    else {
                        params.max = 1
                    }
                    def o = taxonomyService.findObjectsByFamily(delegate, nodeOrPath, params)
                    return o.size() ? o.get(0) : null
                }

                // family can include "taxonomy" arg, string/Taxonomy instance
                domainClazz.metaClass.'static'.findAllByTaxonomyFamily = { nodeOrPath, Map params = null ->
                    taxonomyService.findObjectsByFamily(delegate, nodeOrPath, params)
                }

                // family can include "taxonomy" arg, string/Taxonomy instance
                domainClazz.metaClass.'static'.findByTaxonomyExact = { nodeOrPath, Map params = null ->
                    if (!params) {
                        params = [max:1]
                    }
                    else {
                        params.max = 1
                    }
                    def o = taxonomyService.findObjectsByTaxon(delegate, nodeOrPath, params)
                    return o.size() ? o.get(0) : null
                }

                // family can include "taxonomy" arg, string/Taxonomy instance
                domainClazz.metaClass.'static'.findAllByTaxonomyExact = { nodeOrPath, Map params = null ->
                    taxonomyService.findObjectsByTaxon(delegate, nodeOrPath, params)
                }

                domainClazz.metaClass.addToTaxonomy = { nodeOrPath, taxonomy = null ->
                    def link = taxonomyService.findLink(delegate, nodeOrPath, taxonomy)
                    if (!link) {
                        if (!(nodeOrPath instanceof Taxon)) {
                            nodeOrPath = taxonomyService.createTaxonomyPath(nodeOrPath, taxonomy)
                        }
                        taxonomyService.saveNewLink(delegate, nodeOrPath)
                    }
                }

                domainClazz.metaClass.clearTaxonomies = { ->
                    taxonomyService.removeAllLinks(delegate)
                }

                domainClazz.metaClass.getTaxonomies = { ->
                    taxonomyService.findAllLinks(delegate)*.taxon
                }

                domainClazz.metaClass.hasTaxonomy = { nodeOrPath, taxonomy = null ->
                    taxonomyService.hasLink(delegate, nodeOrPath, taxonomy)
                }

                domainClazz.metaClass.removeTaxonomy = { nodeOrPath, taxonomy = null ->
                    taxonomyService.removeLink(delegate, nodeOrPath, taxonomy)
                }
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
    }

    def onChange = { event ->
        applyDynamicMethods(application)
    }

    def onConfigChange = { event ->
    }
}
