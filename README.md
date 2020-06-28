# Hexlite OWLAPI Plugin

An OWLAPI Plugin for the Hexlite solver.

The plugin is described in the following publication.

Peter Schüller (2020).
A new OWLAPI interface for HEX-Programs applied to Explaining Contingencies in Production Planning.
In: New Foundations for Human-Centered AI, Workshop at ECAI 2020.

# Setup

The easiest way is to use conda. In your base environment run


    $ python3 setup_and_test_within_conda.py <conda-environment>

This will 

* (re-)create a conda environment `<conda-environment>`
* clone a fresh copy of hexlite from the repo
* build the Java API of hexlite
* build this plugin with the Java API
* run an example

Be careful:
* the `<conda-environment>` will be deleted if it exists
* The hexlite subdirectory of this directory will be deleted and cloned anew if it exists

# Development

* Git Repository: https://github.com/hexhex/hexlite-owlapi-plugin

* In Visual Studio Code, it might be necessary to do

    conda init bash
    conda init powershell

  for making the setup work.
