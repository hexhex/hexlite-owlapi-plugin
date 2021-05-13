#!/usr/bin/env python3

import sys
import platform
import setuptools

if platform.python_version().startswith('2'):
  sys.stdout.write("Please use Python 3 instead of Python 2!\n")
  sys.exit(-1)

def readme():
  with open('README', 'wb') as of:
    with open('README.md', 'rb') as i:
      out = b''.join([ line for line in i if not line.startswith(b'[')])
      of.write(out)
      return out.decode('utf8')

readme_txt = readme()

setuptools.setup(name='hexlite-owlapi-plugin',
      version='1.1',
      description='OWLAPI Plugin for the Hexlite Solver',
      long_description=readme_txt,
      classifiers=[
        'Development Status :: 3 - Alpha',
        'Programming Language :: Python :: 3',
        'Intended Audience :: Developers',
        'Intended Audience :: Information Technology',
        'Intended Audience :: Science/Research',
        'License :: OSI Approved :: MIT License',
        'Natural Language :: English',
        'Topic :: Scientific/Engineering :: Artificial Intelligence',
        'Topic :: Scientific/Engineering :: Information Analysis',
        'Topic :: Scientific/Engineering :: Mathematics',
        'Topic :: Utilities',
      ],
      url='https://github.com/hexhex/hexlite-owlapi-plugin',
      author='Peter Schueller',
      author_email='contact@peterschueller.com',
      license='MIT',
      install_requires=[
        'hexlite==1.4.1',
      ],
      zip_safe=False)
