package com.avaje.ebeaninternal.server.core;

import java.util.List;
import java.util.Set;

import com.avaje.ebeaninternal.server.util.ClassPathSearch;
import com.avaje.ebeaninternal.server.util.ClassPathSearchFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches for interesting classes such as Entities, Embedded and ScalarTypes.
 */
public class BootupClassPathSearch {

	private static final Logger logger = LoggerFactory.getLogger(BootupClassPathSearch.class);

	private final Object monitor = new Object();

	private final ClassLoader classLoader;

	private final List<String> packages;
    private final List<String> jars;
	
	private BootupClasses bootupClasses;

	/**
	 * Construct and search for interesting classes.
	 */
	public BootupClassPathSearch(ClassLoader classLoader, List<String> packages, List<String> jars) {
		this.classLoader = (classLoader == null) ? getClass().getClassLoader() : classLoader;
		this.packages = packages;
		this.jars = jars;
	}

	public BootupClasses getBootupClasses() {
		synchronized (monitor) {
			
			if (bootupClasses == null){
				bootupClasses = search();
			}
			
			return bootupClasses;
		}
	}

	/**
	 * Search the classPath for the classes we are interested in.
	 */
	private BootupClasses search() {
		synchronized (monitor) {
			try {
				
				BootupClasses bc = new BootupClasses();

				long st = System.currentTimeMillis();

				ClassPathSearchFilter filter = createFilter();

				ClassPathSearch finder = new ClassPathSearch(classLoader, filter, bc);

				finder.findClasses();
				Set<String> jars = finder.getJarHits();
				Set<String> pkgs = finder.getPackageHits();

				long searchTime = System.currentTimeMillis() - st;

				String msg = "Classpath search hits in jars" + jars + " pkgs" + pkgs + "  searchTime[" + searchTime+ "]";
				logger.info(msg);

				return bc;

			} catch (Exception ex) {
				String msg = "Error in classpath search (looking for entities etc)";
				throw new RuntimeException(msg, ex);
			}
		}
	}

	private ClassPathSearchFilter createFilter() {

		ClassPathSearchFilter filter = new ClassPathSearchFilter();
		filter.addDefaultExcludePackages();

        if (packages != null) {
            for (String packageName : packages) {
                filter.includePackage(packageName);
            }
        }

        if (jars != null) {
            for (String jarName : jars) {
                filter.includeJar(jarName);
            }
        }

		return filter;
	}
}
