# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --backtraces-rescue option" do
  it "prints a backtrace on rescue" do
    file = fixture __FILE__ , 'raise_rescue.rb'
    out = ruby_exe(file, options: "--experimental-options --backtraces-rescue", args: "2>&1")
    out = out.gsub(/:\d+:(in `(?:full_message|get_formatted_backtrace)')/, ':LINE:\1')
    out.should ==  <<~OUTPUT
    rescued at #{file}:10:
    #{file}:10:in `<main>': foo (RuntimeError)
    \tfrom <internal:core> core/exception.rb:LINE:in `full_message'
    \tfrom <internal:core> core/truffle/exception_operations.rb:LINE:in `get_formatted_backtrace'
    OUTPUT
  end

  it "can be used to print backtraces in silent rescues" do
    ruby_exe("1/0 rescue nil", options: "--experimental-options --backtraces-rescue", args: "2>&1").should include("divided by 0 (ZeroDivisionError)")
  end
end
