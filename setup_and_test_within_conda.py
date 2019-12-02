#!/usr/bin/env python3

import os, sys, logging, subprocess, json, traceback

def main():
	s = Setup()
	s.interpret_arguments(sys.argv)
	s.ensure_conda_exists()
	s.recreate_conda_environment()
	s.build_jpype()
	s.reclone_hexlite('javapluginapi')
	s.build_hexlite_java_api()
	s.build_this_plugin()
	s.run_example('koala')

class Setup:
	PYTHONVER='3.7'

	def __init__(self):
		self.config = {}

	@staticmethod
	def __run_shell_get_stdout(cmd, allow_fail=False):
		logging.info("running %s", cmd)
		p = subprocess.Popen('bash -c "%s"' % cmd, shell=True, stdout=subprocess.PIPE, stderr=sys.stderr)
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
		self.__run_shell_get_stdout('conda env remove --name %s' % env, allow_fail=True)
		self.__run_shell_get_stdout('conda create --name %s python=%s ant' % (env, self.PYTHONVER))
		self.__run_shell_get_stdout('conda activate %s' % env)

	def build_jpype(self):
		logging.info('cloning jpype')
		self.__run_shell_get_stdout("rm -rf jpype")
		self.__run_shell_get_stdout("git clone https://github.com/jpype-project/jpype.git")
		self.__run_shell_get_stdout("cd jpype ; python setup.py sdist")

		logging.info('building jpype into conda env')		
		path = self.__run_shell_get_stdout('source activate %s ; echo $CONDA_PREFIX' % env)
		ld_orig = path+'/compiler_compat/ld'
		ld_temp = path+'/compiler_compat/ld_temp'
		os.rename(ld_orig, ld_temp) # conda bug, see readme in hexlite repo
		try:
			self.__run_shell_get_stdout('source activate %s ; cd jpype ; pip install dist/* --global-option=--disable-numpy' % env)
		finally:
			os.rename(ld_temp, ld_orig) # restore conda env

	def reclone_hexlite(self):
		pass

	def build_hexlite_java_api(self):
		self.__run_shell_get_stdout('source activate %s ; conda install -c potassco clingo' % env)


logging.basicConfig(level=logging.INFO)
try:
	main()
except Exception:
	logging.error(traceback.format_exc())