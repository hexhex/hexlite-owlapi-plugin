# Hexlite OWLAPI Plugin

An OWLAPI Plugin for the Hexlite solver

This software is under construction.

# Setup

Currently, try the following:

    setup_and_test_within_conda.py <conda-environment>

This will 

* (re-)create a conda environment `<conda-environment>`
* clone a fresh copy of hexlite from the repo
* build the Java API of hexlite
* build this plugin with the Java API
* run an example

Be careful:
* the `<conda-environment>` will be deleted if it exists
* hexlite will be deleted and cloned anew if it exists

# Development

* In Visual Studio Code, it might be necessary to do

    conda init bash
    conda init powershell

  for making the setup work.