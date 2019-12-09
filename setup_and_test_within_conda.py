#!/usr/bin/env python3

import os, sys, logging, subprocess, json, traceback

def main():
	s = Setup()
	s.interpret_arguments(sys.argv)
	s.ensure_conda_exists()
	# s.recreate_conda_environment()
	# s.build_jpype()
	# s.reclone_hexlite('javapluginapi')
	# s.build_hexlite_java_api()
	# s.install_hexlite()
	s.build_this_plugin()
	s.get_classpath() # this MUST run so that the examples can run
	s.run_example('koala', 'querykoala1.hex')

class Setup:
	PYTHONVER='3.7'
	JPYPE_REF='69060be'

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

	def recreate_conda_environment(self):
		env = self.config['env']
		assert(env != 'base')
		self.__run_shell_get_stdout('conda env remove --name %s >&2' % env, allow_fail=True)
		self.__run_shell_get_stdout('conda create --name %s -c potassco clingo python=%s ant maven >&2' % (env, self.PYTHONVER))
		self.__run_shell_get_stdout('source activate %s' % env)

	def build_jpype(self):
		logging.info('cloning jpype')
		self.__run_shell_get_stdout("rm -rf jpype")
		self.__run_shell_get_stdout("git clone https://github.com/jpype-project/jpype.git >&2 && cd jpype && git checkout %s >&2" % self.JPYPE_REF)
		self.__run_shell_get_stdout("cd jpype && python setup.py sdist >&2")

		logging.info('building jpype into conda env')		
		env = self.config['env']
		# $ is interpreted by outer shell, but we want it to be interpreted by inner shell (the 'bash' started by __run_shell_get_stdout)
		path = self.__run_shell_get_stdout('source activate %s && echo \\$CONDA_PREFIX' % env).strip()
		logging.info('got path %s', path)
		ld_orig = path+'/compiler_compat/ld'
		ld_temp = path+'/compiler_compat/ld_temp'
		logging.info("hiding %s as %s", ld_orig, ld_temp)
		os.rename(ld_orig, ld_temp) # conda bug, see readme in hexlite repo
		try:
			self.__run_shell_get_stdout('source activate %s && cd jpype && pip install dist/* --global-option=--disable-numpy >&2' % env)
		finally:
			os.rename(ld_temp, ld_orig) # restore conda env

	def reclone_hexlite(self, ref):
		logging.info('cloning hexlite')
		self.__run_shell_get_stdout("rm -rf hexlite")
		self.__run_shell_get_stdout("git clone https://github.com/hexhex/hexlite.git >&2 && cd hexlite && git checkout %s >&2" % ref)

	def build_hexlite_java_api(self):
		logging.info('building and installing hexlite Java API')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd hexlite/java-api && mvn clean compile package install >&2" % env)

	def install_hexlite(self):
		logging.info('installing hexlite')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd hexlite/ && python setup.py develop --user >&2" % env)

	def build_this_plugin(self):
		logging.info('building OWLAPI Plugin')
		env = self.config['env']
		self.__run_shell_get_stdout("source activate %s && cd plugin && mvn clean compile package >&2" % env)

	def get_classpath(self):
		self.config['classpath'] = self.__run_shell_get_stdout("cd plugin && mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q")
		logging.info("got classpath %s", self.config['classpath'])

	def run_example(self, directory, hexfile):
		env = self.config['env']
		call = "hexlite --pluginpath hexlite/plugins/ --plugin javaapiplugin at.ac.tuwien.kr.hexlite.OWLAPIPlugin"
		#call += ' --verbose'
		stdout = self.__run_shell_get_stdout("source activate %s && %s -- %s" % (env, call, os.path.join('examples', directory, hexfile)))
		sys.stdout.write(stdout)

logging.basicConfig(level=logging.INFO)
try:
	main()
except Exception:
	logging.error(traceback.format_exc())