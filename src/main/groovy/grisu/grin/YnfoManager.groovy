package grisu.grin

import grisu.grin.model.Grid
import grisu.grin.model.resources.*
import grisu.jcommons.configuration.CommonGridProperties
import grisu.jcommons.constants.GridEnvironment
import grisu.jcommons.model.info.*
import groovy.util.logging.Slf4j

@Slf4j('myLogger')
class YnfoManager  {

	class UpdateInfoTask extends TimerTask {
		public void run() {
			myLogger.debug("Kicking of automated info refresh...");
			refreshAndWait()
			myLogger.debug("Automated info refresh finished.");
		}
	}


	static void printConnections(def res) {

		res.getConnections().each { c ->

			println '\t\t'+c.toString()

			//			AbstractResource.getRecursiveConnections(c).each { it ->
			//				println '\t\t\t'+c+': '+ it.toString()
			//			}
		}
	}

	static void main (args) {

		//def ym = new YnfoManager('/home/markus/Workspaces/Goji/grin/src/main/resources/nesi.groovy')
		//		def ym = new YnfoManager('/home/markus/Workspaces/Goji/grin/src/main/resources/testbed.groovy')
		//		def ym = new YnfoManager('https://raw.github.com/nesi/nesi-grid-info/develop/nesi.groovy')
		//		def ym = new YnfoManager(null)
		//		def ym = new YnfoManager('/home/markus/Workspaces/Goji/grin/src/test/resources/test_2_sites.config.groovy')

		//		ym.startPeriodicRefresh(2)

		if (args.length == 0 ) {
			println 'No url specified, using default testbed config...'
			args = [
				'https://raw.github.com/nesi/nesi-grid-info/develop/testbed_info.groovy'
			]
		}

		def url = args[0]
		println 'Using info config from: "'+url+'..."'
		def ym = new YnfoManager(url)

		Grid grid = ym.getGrid()

		println 'Sites:'
		for ( def site : grid.getSites() ) {
			println '\t'+site.toString()
			//			printConnections(site)
		}

		println 'VOs:'
		for ( def vo : grid.getVos() ) {
			println '\t'+vo.getVoName()
			//			printConnections(vo)
		}

		println 'Groups:'
		for ( def group : grid.getGroups() ) {
			println '\t' + group.getFqan()
			//			printConnections(group)
		}

		println 'Filesystems:'
		for ( def fs : grid.getFilesystems() ) {
			println '\t' + fs.getUrl()
			//			printConnections(fs)
		}

		println 'Directories:'
		for ( def dir : grid.getDirectorys() ) {
			println '\t' + dir.getUrl()
			//			printConnections(dir)
		}

		println 'Gateways:'
		for ( def gw : grid.getGateways() ) {
			println '\t' + gw.toString()
			//			printConnections(gw)
		}

		println 'Queues:'
		for ( def q : grid.getQueues() ) {
			println '\t' + q.toString()
			//			printConnections(q)
		}

		println 'Applications:'
		for ( def app : grid.getApplications() ) {
			println '\t' + app.getName()
			//			printConnections(app)
		}

		println 'Versions:'
		for ( def v : grid.getVersions() ) {
			println '\t' + v.getVersion()
			//			printConnections(v)
		}

		println 'Packages:'
		for ( def p : grid.getPackages() ) {
			println '\t' + p.getName()
			//			printConnections(p)
		}

		System.exit(0)
	}


	// min threshold for updating info (in seconds)
	static def threshold = 4


	def grid = new Grid()
	String path = null

	def timer = new Timer()
	def task

	Date lastUpdated

	public YnfoManager() {
		this(null)
	}

	public YnfoManager(String pathToConfig) {
		path = pathToConfig
		initialize(path)
	}

	public refreshAndWait() {
		initialize(path)
	}

	public void refresh() {
		Thread t = new Thread() {
					public void run() {
						initialize(path)
					}
				}
		t.setName('YnfoUpdate')
		t.setDaemon(true)
		t.start()
	}

	public startPeriodicRefresh(int seconds) {

		if ( task ) {
			task.cancel()
		}

		task = timer.scheduleAtFixedRate(new UpdateInfoTask(), seconds*1000, seconds*1000)
	}


	protected synchronized void initialize(String pathToConfig) {

		Date now = new Date()

		if ( !lastUpdated ||  (now.getTime() - lastUpdated.getTime() > (threshold*1000) ) ) {

			Version.clearCache()
			Middleware.clearCache()
			Executable.clearCache()
			Application.clearCache()
			
			def gridtemp = new Grid()

			try {
				def config

				if ( ! pathToConfig ) {
					pathToConfig = CommonGridProperties.getDefault().getGridInfoConfig()
				}


				// check whether there is default "grid.groovy" file somewhwere
				if ( ! pathToConfig ) {
					File file = GridEnvironment.getGridInfoConfigFile()
					if ( file.exists() ) {
						pathToConfig = file.getAbsolutePath()
					}
				}

				if ( ! pathToConfig ) {
					pathToConfig = 'testbed'
				}

				myLogger.debug('Updating info for path/alias: '+pathToConfig)

				if ( "testbed".equals(pathToConfig) ) {
					//					InputStream is = getClass().getResourceAsStream('/testbed.groovy')
					//					config = new ConfigSlurper().parse(is.getText())
					//					config = new ConfigSlurper().parse(new URL('https://raw.github.com/nesi/nesi-grid-info/develop/testbed.groovy'))
					pathToConfig = 'https://raw.github.com/nesi/nesi-grid-info/master/testbed_info.groovy'
				} else if ("nesi".equals(pathToConfig)) {
					//					InputStream is = getClass().getResourceAsStream('/nesi.groovy')
					//					config = new ConfigSlurper().parse(is.getText())
					//					config = new ConfigSlurper().parse(new URL('https://raw.github.com/nesi/nesi-grid-info/develop/nesi.groovy'))
					pathToConfig = 'https://raw.github.com/nesi/nesi-grid-info/master/nesi_info.groovy'

				}
				if ( pathToConfig.startsWith('http') ) {
					myLogger.debug 'Retrieving remote config from "'+pathToConfig+'"...'
					config = new ConfigSlurper().parse(new URL(pathToConfig))
				} else {
					myLogger.debug 'Using local config from "'+pathToConfig+'"...'
					config = new ConfigSlurper().parse(new File(pathToConfig).toURL())
				}




				for (def e in config) {

					def name = e.key
					def object = e.value

					if ( object instanceof AbstractResource ) {
						//println 'setting alias: '+name
						object.setAlias(name)
					}

					switch(object.class) {

						case Directory.class:
							gridtemp.addDirectory(object)
							break

						case Queue.class:
							gridtemp.addQueue(object)
							break
					}
				}

				myLogger.debug('Grid object created for path/alias: '+pathToConfig+'. Validating...')

				gridtemp.validate()
				myLogger.debug('Grid object validated for path/alias: '+pathToConfig)
			} catch (all) {
				myLogger.error("Can't build Grid object, probably info config broken: "+all.getLocalizedMessage(), all)
				return
			}
			this.grid = gridtemp
			lastUpdated = new Date()
		}
	}

	def download(String address)
	{
		tempfile = new FileOutputStream(File.createTempFile("temp", address.tokenize("/")[-1]))
		def out = new BufferedOutputStream(file)
		out << new URL(address).openStream()
		out.close()
		tempfile
	}


	public Grid getGrid() {
		return grid;
	}
}
