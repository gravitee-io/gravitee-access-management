#!/bin/bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

readonly WORKDIR="$HOME/graviteeio-am-demo"
readonly DIRNAME=`dirname $0`
readonly PROGNAME=`basename $0`
readonly color_title='\033[32m'
readonly color_text='\033[1;36m'

# OS specific support (must be 'true' or 'false').
declare cygwin=false
declare darwin=false
declare linux=false
declare dc_exec="docker-compose up"

welcome() {
    echo
    echo -e " ${color_title}  ______                  _                  _                                                     \033[0m"
    echo -e " ${color_title} / _____)                (_)_               (_)                                                    \033[0m"
    echo -e " ${color_title}| /  ___  ____ ____ _   _ _| |_  ____ ____   _  ___                                                \033[0m"
    echo -e " ${color_title}| | (___)/ ___) _  | | | | |  _)/ _  ) _  ) | |/ _ \                                               \033[0m"
    echo -e " ${color_title}| \____/| |  ( ( | |\ V /| | |_( (/ ( (/ / _| | |_| |                                              \033[0m"
    echo -e " ${color_title} \_____/|_|   \_||_| \_/ |_|\___)____)____|_)_|\___/                                               \033[0m"
    echo -e " ${color_title}                                    \033[0m${color_text}http://gravitee.io\033[0m"
    echo -e " ${color_title}                                     ______                                                        \033[0m"
    echo -e " ${color_title}   /\                               |  ___ \                                                 _     \033[0m"
    echo -e " ${color_title}  /  \   ____ ____ ____  ___  ___   | | _ | | ____ ____   ____  ____  ____ ____   ____ ____ | |_   \033[0m"
    echo -e " ${color_title} / /\ \ / ___) ___) _  )/___)/___)  | || || |/ _  |  _ \ / _  |/ _  |/ _  )    \ / _  )  _ \|  _)  \033[0m"
    echo -e " ${color_title}| |__| ( (__( (__( (/ /|___ |___ |  | || || ( ( | | | | ( ( | ( ( | ( (/ /| | | ( (/ /| | | | |__  \033[0m"
    echo -e " ${color_title}|______|\____)____)____|___/(___/   |_||_||_|\_||_|_| |_|\_||_|\_|| |\____)_|_|_|\____)_| |_|\___) \033[0m"
    echo -e " ${color_title}                                                              (_____|                              \033[0m"
    echo -e " ${color_title} 																								   \033[0m"

    echo
}

init_env() {
    local dockergrp
    # define env
    case "`uname`" in
        CYGWIN*)
            cygwin=true
            ;;

        Darwin*)
            darwin=true
            ;;

        Linux)
            linux=true
            ;;
    esac

    # test if docker must be run with sudo
    dockergrp=$(groups | grep -c docker)
    if [[ $darwin == false && $dockergrp == 0 ]]; then
        dc_exec="sudo $dc_exec";
    fi
}

init_dirs() {
    echo "Init log directory in $WORKDIR ..."
    mkdir -p "$WORKDIR/logs/"
    echo 
}

main() {
    welcome
    init_env
    if [[ $? != 0 ]]; then
        exit 1
    fi
    set -e
    init_dirs
    pushd $WORKDIR > /dev/null
        echo "Download required files ..."
        mkdir -p config
        curl -L https://raw.githubusercontent.com/gravitee-io/graviteeio-access-management/master/docker/compose/docker-compose.yml -o "docker-compose.yml"
        cd config && { curl -O https://raw.githubusercontent.com/gravitee-io/graviteeio-access-management/master/docker/compose/config/constants.json ; cd -; }
        cd config && { curl -O https://raw.githubusercontent.com/gravitee-io/graviteeio-access-management/master/docker/compose/config/nginx.conf ; cd -; }
        echo
        echo "Launch Gravitee.io Access Management ..."
        $dc_exec
    popd > /dev/null
}

main
