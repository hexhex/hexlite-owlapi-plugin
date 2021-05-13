#!/usr/bin/env python3

import os, sys, logging, subprocess, json, traceback

def main():
	s = Setup()
	s.interpret_arguments(sys.argv)
	s.ensure_java_maven_exists()
	s.ensure_conda_exists()
	s.recreate_conda_environment()
	s.install_via_pip('"clingo>=5.5.0" "jpype1>=1.2.1"')
	s.reclone_hexlite('v1.4.0')
	s.build_hexlite_java_api()
	s.install_hexlite()
	s.build_this_plugin()

	# either use full classpath provided by maven (that is slow)
	#s.get_classpath()
	# or use only jar with dependencies (created by maven-shade-plugin, faster than asking mvn for classpath)
	# the "./" is for being able to put log4j2.xml into ./
	cwd = os.getcwd()
	s.config['classpath'] = '%s/:%s/plugin/target/owlapiplugin-1.1.0.jar' % (cwd, cwd)

	s.run_example('koala', ['querykoala1.hex'])
	s.run_example('koala', ['querykoala2.hex'])
	s.run_example('factory', ['domain.hex', 'query_allpainted.hex'])
	s.run_example('factory', ['domain.hex', 'query_deactivatable.hex'])
	s.run_example('factory', ['domain.hex', 'query_skippable.hex'])

class Setup:
	PYTHONVER='3.7'
	# for public access
	#HEXLITE_CLONE_SOURCE='https://github.com/hexhex/hexlite.git'
	# for developer access (including push possibility)
	HEXLITE_CLONE_SOURCE='git@github.com:hexhex/hexlite.git'

	def __init__(self):
		self.config = {}

	def __run_shell_get_stdout(self, cmd, allow_fail=False, wd=None):
		logging.info("running %s", cmd)
		env = os.environ.copy()
		if 'classpath' in self.config:
			# the first path is for the built plugin
			# the second path is for the logging configuration
			env['CLASSPATH'] = self.config['classpath'] + ':plugin/target/classes/:plugin/'
		cwd = os.getcwd()
		if wd is not None:
			cwd = wd
		p = subprocess.Popen('bash -c "%s"' % cmd, env=env, cwd=cwd, shell=True, stdout=subprocess.PIPE, stderr=sys.stderr)
		stdout = p.communicate()[0].decode('utf-8')
		if not allow_fail and p.returncode != 0:
			raise Exception("failed program: "+cmd)
		return stdout

	def interpret_arguments(self, argv):
		if len(argv) != 2:
			raise Exception("expect exactly the following arguments: <conda-environment>")
		self.config['env'] = argv[1]
		logging.info("config is %s", self.config)

	def ensure_conda_exists(self):
		ver = self.__run_shell_get_stdout('conda --version')
		logging.info("found conda version %s", ver)

	def ensure_java_maven_exists(self):
		ver = self.__run_shell_get_stdout('java --version')
		logging.info("found java version %s", ver)
		ver = self.__run_shell_get_stdout('mvn --version')
		logging.info("found maven version %s", ver)

	def recreate_conda_environment(self):
		env = self.config['env']
		assert(env != 'base')
		self.__run_shell_get_stdout('conda env remove -y --name %s >&2' % env, allow_fail=True)
		self.__run_shell_get_stdout('conda create -y --name %s python=%s pandas >&2' % (env, self.PYTHONVER))
		self.__run_shell_get_stdout('source activate %s' % env)

	def install_jpype_via_build(self, github_ref):
		env = self.config['env']

		logging.info('cloning jpype')
		self.__run_shell_get_stdout("rm -rf jpype")
		self.__run_shell_get_stdout("git clone https://github.com/jpype-project/jpype.git >&2 && cd jpype && git checkout %s >&2" % github_ref)
		self.__run_shell_get_stdout("source activate %s && cd jpype && python setup.py sdist >&2" % (env,))

		logging.info('building jpype into conda env')		
		# $ is interpreted by outer shell, but we want it to be interpreted by inner shell (the 'bash' started by __run_shell_get_stdout)
		path = self.__run_shell_get_stdout('source activate %s && echo \\$CONDA_PREFIX' % env).strip()
		logging.info('got path %s', path)
		ld_orig = path+'/compiler_compat/ld'
		ld_temp = path+'/compiler_compat/ld_temp'
		logging.info("hiding %s as %s", ld_orig, ld_temp)
		os.rename(ld_orig, ld_temp) # conda bug, see readme in hexlite repo
		try:
			self.__run_shell_get_stdout('source activate %s && cd jpype && pip install dist/* >&2' % env)
		finally:
			os.rename(ld_temp, ld_orig) # restore conda env

	def install_jpype_via_conda(self, version=None):
		env = self.config['env']
		ver = ''
		if version is not None:
			ver = '='+version
		self.__run_shell_get_stdout("source activate %s && conda install -y -c conda-forge jpype1%s >&2" % (env, ver))

	def install_via_pip(self, what='"clingo>=5.5.0" "jpype1>=1.2.1"'):
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && pip3 install %s >&2" % (env, what))

	def reclone_hexlite(self, github_ref):
		logging.info('cloning hexlite')
		self.__run_shell_get_stdout("rm -rf hexlite")
		self.__run_shell_get_stdout("git clone %s >&2 && cd hexlite && git checkout %s >&2" % (self.HEXLITE_CLONE_SOURCE,github_ref) )

	def build_hexlite_java_api(self):
		logging.info('building and installing hexlite Java API')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd hexlite/ && mvn clean compile package install >&2" % env)

	def install_hexlite(self):
		logging.info('installing hexlite')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd hexlite/ && python setup.py install >&2" % env)

	def build_this_plugin(self):
		logging.info('building OWLAPI Plugin')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd plugin && mvn clean compile package >&2" % env)

	def get_classpath(self):
		env = self.config['env']
		self.config['classpath'] = self.__run_shell_get_stdout("source activate %s && cd plugin && mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q" % env)
		logging.info("got classpath %s", self.config['classpath'])

	def run_example(self, directory, hexfiles):
		env = self.config['env']
		cwd = os.getcwd()
		call = "hexlite --pluginpath %s/hexlite/plugins/ --plugin javaapiplugin at.ac.tuwien.kr.hexlite.OWLAPIPlugin --number 33" % cwd 
		#call += ' --verbose'
		call += ' --stats'
		#call += ' --noeatomlearn'
		logging.warning("TODO fix bug in FLP checker (parser?)")
		call += ' --flpcheck=none'
		execdir = os.path.join('examples', directory)
		stdout = self.__run_shell_get_stdout("cd %s && source activate %s && %s -- %s" % (execdir, env, call, ' '.join(hexfiles)))
		sys.stdout.write(stdout)

logging.basicConfig(level=logging.INFO)
try:
	main()
except Exception:
	logging.error(traceback.format_exc())

# vim:noet:nolist:
