ENV['ROOT'] = File.dirname __FILE__

ENV['BUNDLE_GEMFILE']="#{ENV['ROOT']}/Gemfile"

require "rubygems"
require "bundler/setup"

Bundler.require

$:.unshift "#{ENV['ROOT']}/lib"
require "datasource"
require "picture"
require "declare"

$:.unshift ENV['ROOT']
require "portal"
require "config/canvas"

run Portal::Monitor
