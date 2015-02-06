
log4j = {
    appenders {
        // Grails default stdout conversion pattern truncates logger's name (it uses '%c{2}'). Therefore, it is redefined here to get full logger's name.
        console name: "stdout", layout: pattern(conversionPattern: "%d [%t] %-5p %c %x - %m%n")
    }

    error 'org.codehaus.groovy.grails.web.servlet',        // controllers
          'org.codehaus.groovy.grails.web.pages',          // GSP
          'org.codehaus.groovy.grails.web.sitemesh',       // layouts
          'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
          'org.codehaus.groovy.grails.web.mapping',        // URL mapping
          'org.codehaus.groovy.grails.commons',            // core / classloading
          'net.sf.ehcache.hibernate'

    info 'org.codehaus.groovy.grails.orm.hibernate',       // hibernate integration
         'org.springframework',
         'org.hibernate',
         'grails.app',                                     // all application artifacts
         'org.codehaus.groovy.grails.plugins'              // plugins

    all 'grails.app'

    // Uncomment following two lines to see hibernate generated sql in stdout
//    debug 'org.hibernate.SQL'
//    trace 'org.hibernate.type.descriptor.sql.BasicBinder'
}
