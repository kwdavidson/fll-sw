/*
 * Copyright (c) 2014 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

(function($) {
	if (!$) {
		throw new Error("jQuery needs to be loaded!");
	}
	if (!$.jStorage) {
		throw new Error("jStorage needs to be loaded!");
	}

	var STORAGE_PREFIX = "fll.subjective";

	// //////////////////////// PRIVATE INTERFACE ////////////////////////

	var _subjectiveCategories;
	var _tournament;
	var _teams;

	function _init_variables() {
		_subjectiveCategories = {};
		_tournament = null;
		_teams = {};
	}

	function _log(str) {
		if (typeof (console) != 'undefined') {
			console.log(str);
		}
	}

	/**
	 * Clear anything from local storage with a prefix of STORAGE_PREFIX.
	 */
	function _clear_local_storage() {
		$.each($.jStorage.index(), function(index, value) {
			if (value.substring(0, STORAGE_PREFIX.length) == STORAGE_PREFIX) {
				$.jStorage.deleteKey(value);
			}
		});
	}

	function _loadFromDisk() {
		_init_variables();

		_log("Loading from disk");

		var value = $.jStorage.get(STORAGE_PREFIX + "_subjectiveCategories");
		if (null != value) {
			_subjectiveCategories = value;
		}
	}

	function _save() {
		_log("Save not yet implemented");
	}

	function _loadTournament() {
		_tournament = null;

		return $.getJSON("../api/Tournaments/current", function(tournament) {
			_tournament = tournament;
		});
	}

	function _loadTeams() {
		_teams = {};

		return $.getJSON("../api/TournamentTeams", function(teams) {
			$.each(teams, function(i, team) {
				_teams[team.teamNumber] = team;
			});
		});
	}

	/**
	 * Load the subjective categories.
	 * 
	 * @returns the promise for the ajax query
	 */
	function _loadSubjectiveCategories() {
		_subjectiveCategories = {};

		return $
				.getJSON(
						"../api/ChallengeDescription/SubjectiveCategories",
						function(subjectiveCategories) {
							$
									.each(
											subjectiveCategories,
											function(i, scoreCategory) {
												_subjectiveCategories[scoreCategory.name] = scoreCategory;
											});
						});
	}

	// //////////////////////// PUBLIC INTERFACE /////////////////////////
	$.subjective = {

		/**
		 * Clear all data from local storage.
		 */
		clearAllData : function() {
			_clear_local_storage();
			_init_variables();
		},

		/**
		 * @param doneCallback
		 *            called with no arguments on success
		 * @param failCallback
		 *            called with no arguments on failure
		 */
		loadFromServer : function(doneCallback, failCallback) {
			_init_variables();

			console.log("Loading from server");

			var waitList = []
			waitList.push(_loadSubjectiveCategories());
			waitList.push(_loadTournament());
			waitList.push(_loadTeams());

			$.when.apply($, waitList).done(function() {
				doneCallback();
			}).fail(function() {
				failCallback();
			});
		},

		/**
		 * @return list of subjective categories
		 */
		getSubjectiveCategories : function() {
			var retval = [];
			$.each(_subjectiveCategories, function(i, val) {
				retval.push(val);
			});
			return retval;
		},

		/**
		 * @return list of teams
		 */
		getTeams : function() {
			var retval = [];
			$.each(_teams, function(i, val) {
				retval.push(val);
			});
			return retval;
		},

		/**
		 * Find a team by number.
		 */
		lookupTeam : function(teamNum) {
			return _teams[teamNum];
		},

		log : function(str) {
			_log(str);
		},

		/**
		 * @return current stored tournament
		 */
		getTournament : function() {
			return _tournament;
		},

		/**
		 * Get the current tournament from the server.
		 * 
		 * @param doneCallback
		 *            called with the server tournament as the argument
		 * @param failCallback
		 *            called with no arguments
		 */
		getServerTournament : function(doneCallback, failCallback) {
			return $.getJSON("../api/Tournaments/current",
					function(tournament) {
						doneCallback(tournament);
					}).fail(function() {
				failCallback();
			});
		},

		/**
		 * @return true if stored data exists
		 */
		storedDataExists : function() {
			if (null == _subjectiveCategories) {
				return false;
			} else if (_subjectiveCategories.length == 0) {
				return false;
			} else {
				return true;
			}
		},

	};

	// ///// Load data //////////
	_loadFromDisk();
})(window.jQuery || window.$);
